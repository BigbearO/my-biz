package project

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/AlecAivazis/survey/v2"
	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/buf"
	"github.com/wind-scaffold/wind/internal/pkg"
	gens "github.com/wind-scaffold/wind/pkg/generators"
)

// CmdProject represents the project command.
var CmdProject = &cobra.Command{
	Use:          "project [name]",
	Aliases:      []string{"proj"},
	Short:        "create a new project scaffold",
	Long:         "Create a project using the repository template. Example: gow new project helloworld",
	Args:         cobra.ExactArgs(1),
	RunE:         Run,
	SilenceUsage: true,
}

var (
	repoURL     string
	templateDir string
	branch      string
	timeout     string
	moduleName  string
	nomod       bool
	skipCI      bool
)

const (
	// GithubRepoURL kratos-template 模板仓(public,默认远端)。
	// 模板内部 module 占位符仍为 TemplateModuleName,见 project.go。
	GithubRepoURL = "https://github.com/BigbearO/kratos-template.git"
)

// defaultTemplateSource 解析默认模板源(优先级低于 --template 与 -r):
//  1. 环境变量 WIND_TEMPLATE(本地模板目录)
//  2. CWD 及其上级的 kratos-template 目录(开发期免 clone,改模板→立刻验证)
//  3. 远端 GitHub 模板仓(统一加 gh-proxy 前缀,常量见 deps.go,可替换)
func defaultTemplateSource() string {
	if env := os.Getenv("WIND_TEMPLATE"); env != "" && isLocalTemplateDir(env) {
		return env
	}
	for _, cand := range []string{"./kratos-template", "../kratos-template", "../../kratos-template"} {
		if isLocalTemplateDir(cand) {
			if abs, err := filepath.Abs(cand); err == nil {
				return abs
			}
			return cand
		}
	}
	return gens.ApplyGHProxy(GithubRepoURL)
}

func init() {
	timeout = "60s"

	CmdProject.Flags().StringVarP(&repoURL, "repo-url", "r", GithubRepoURL, "layout repo")
	CmdProject.Flags().StringVar(&templateDir, "template", "", "template source: a local directory or a git repo URL (overrides -r)")
	CmdProject.Flags().StringVarP(&branch, "branch", "b", branch, "repo branch")
	CmdProject.Flags().StringVarP(&timeout, "timeout", "t", timeout, "time out")
	CmdProject.Flags().StringVarP(&moduleName, "module", "m", moduleName, "set go module name, if not set, use project name")
	CmdProject.Flags().BoolVarP(&nomod, "nomod", "", nomod, "retain go mod")
	CmdProject.Flags().BoolVar(&skipCI, "no-ci", false, "skip emitting the GitHub Actions CI workflow (.github/workflows/ci.yml)")
}

func Run(cmd *cobra.Command, args []string) error {
	// 模板源优先级:--template > -r > 默认(开发期本地优先,否则 GitHub+gh-proxy)。
	if templateDir != "" {
		repoURL = templateDir
	} else if !cmd.Flags().Changed("repo-url") {
		repoURL = defaultTemplateSource()
	}
	// github.com 直连在本环境会被重置,统一走 gh-proxy 前缀(幂等,已带前缀则跳过)。
	repoURL = gens.ApplyGHProxy(repoURL)

	t, err := time.ParseDuration(timeout)
	if err != nil {
		return fmt.Errorf("invalid timeout value %q: %w", timeout, err)
	}

	parentCtx := cmd.Context()
	if parentCtx == nil {
		parentCtx = context.Background()
	}
	ctx, cancel := context.WithTimeout(parentCtx, t)
	defer cancel()

	name := ""
	if len(args) == 0 {
		prompt := &survey.Input{
			Message: "What is project name ?",
			Help:    "Created project name.",
		}
		err = survey.AskOne(prompt, &name)
		if err != nil || name == "" {
			return nil
		}
	} else {
		name = args[0]
	}

	projectName, workingDir := processProjectParams(name)

	if isDirExists(workingDir, projectName) {
		fmt.Printf("🚫 %s already exists\n", projectName)
		prompt := &survey.Confirm{
			Message: "📂 Do you want to override the folder ?",
			Help:    "Delete the existing folder and create the project.",
		}
		var override bool
		e := survey.AskOne(prompt, &override)
		if e != nil {
			return nil
		}
		if !override {
			return nil
		}
		_ = os.RemoveAll(filepath.Join(workingDir, projectName))
	}

	fmt.Printf("🚀 Creating project %s, layout repo is %s, please wait a moment.\n\n", projectName, repoURL)

	p := &Project{
		Name:   projectName,
		Module: projectName,
	}
	if moduleName != "" {
		p.Module = moduleName
	}

	done := make(chan error, 1)
	go func() {
		if !nomod {
			if err := p.New(ctx, workingDir, repoURL, branch); err != nil {
				done <- err
				return
			}
			if !skipCI {
				if written, err := writeCIWorkflow(filepath.Join(workingDir, projectName)); err != nil {
					done <- err
					return
				} else if written {
					log.Printf("🧭 CI workflow emitted at .github/workflows/ci.yml\n")
				}
			}
			done <- nil
			return
		}
		projectRoot := getGoModProjectRoot(workingDir)
		if goModIsNotExistIn(projectRoot) {
			done <- fmt.Errorf("🚫 go.mod don't exists in %s", projectRoot)
			return
		}

		packagePath, e := filepath.Rel(projectRoot, filepath.Join(workingDir, projectName))
		if e != nil {
			done <- fmt.Errorf("🚫 failed to get relative path: %v", e)
			return
		}
		packagePath = strings.ReplaceAll(packagePath, "\\", "/")

		mod, e := pkg.ModulePath(filepath.Join(projectRoot, "go.mod"))
		if e != nil {
			done <- fmt.Errorf("🚫 failed to parse `go.mod`: %v", e)
			return
		}
		// Get the relative path for adding a project based on Go modules
		p.Path = filepath.Join(strings.TrimPrefix(workingDir, projectRoot+"/"), p.Name)
		done <- p.Add(ctx, workingDir, repoURL, branch, mod, packagePath)
	}()

	select {
	case <-ctx.Done():
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return fmt.Errorf("project creation timed out")
		}
		return fmt.Errorf("failed to create project: %w", ctx.Err())

	case createErr := <-done:
		if createErr != nil {
			return fmt.Errorf("failed to create project: %w", createErr)
		}

		if err = pkg.GoModTidy(ctx, filepath.Join(workingDir, projectName)); err != nil {
			return fmt.Errorf("failed to run `go mod tidy`: %w", err)
		}

		if err = buf.GenerateFromPath(ctx, filepath.Join(workingDir, projectName, "api")); err != nil {
			return fmt.Errorf("failed to generate api code: %w", err)
		}

		// 写项目级脚手架配置(module/proto_dir),proto 命令与 generate 均从它解析目录。
		if err = writeScaffoldConfig(filepath.Join(workingDir, projectName), p.Module); err != nil {
			return fmt.Errorf("failed to write .scaffold.yaml: %w", err)
		}

		fmt.Printf("✅ Project %s created successfully at %s\n", projectName, filepath.Join(workingDir, projectName))
		return nil
	}
}

// writeScaffoldConfig 写 .scaffold.yaml(已存在则跳过,尊重用户手改)。
func writeScaffoldConfig(projectDir, module string) error {
	path := filepath.Join(projectDir, ".scaffold.yaml")
	if _, err := os.Stat(path); err == nil {
		return nil
	}
	if module == "" {
		module = "REPLACE_ME"
	}
	content := fmt.Sprintf(`# wind CLI 项目级配置;所有 proto 相关命令先读本文件。
# proto_dir:proto 源目录,默认模板自带;可指向公共 proto 目录(相对/绝对路径)。
module: %s
proto_dir: %s
`, module, gens.DefaultProtoDir)
	return os.WriteFile(path, []byte(content), 0o644)
}
