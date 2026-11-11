package buf

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"

	"github.com/wind-scaffold/wind/internal/pkg"
	"github.com/wind-scaffold/wind/internal/scaffold"
)

const (
	defaultBufConfigFile    = "buf.yaml"
	bufLockFile             = "buf.lock"
	defaultBufGenConfigFile = "buf.gen.yaml"
)

// 命令行参数(cmd 层注册绑定到这两个变量)。
var (
	GenPaths []string
	GenLangs string
)

func RunGenerate(cmd *cobra.Command, args []string) error {
	inspector, err := pkg.NewModuleInspectorFromGo(cmd.Context(), "")
	if err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: %s\033[m\n", err.Error())
		return err
	}

	// 先在模块根目录运行 `go mod tidy`
	if err = pkg.GoModTidy(cmd.Context(), inspector.Root); err != nil {
		return err
	}

	// proto_dir 来自 .scaffold.yaml(默认 api/protos,可指向公共目录)
	cfg, root, err := scaffold.Find()
	if err != nil {
		return err
	}
	protoDir := cfg.AbsProtoDir(root)
	if !pkg.IsDirExists(protoDir) {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: proto 目录不存在: %s\033[m\n", protoDir)
		return fmt.Errorf("proto 目录不存在: %s", protoDir)
	}
	// 默认模式下保持 gow 行为(以 api/ 为 buf workspace);外置模式 workspace 跟随 proto_dir。
	genDir := protoDir
	if filepath.Base(protoDir) == "protos" && pkg.IsDirExists(filepath.Dir(protoDir)) &&
		isBufConfigExists(filepath.Dir(protoDir)) {
		genDir = filepath.Dir(protoDir)
	}

	return GenerateFromPath(cmd.Context(), genDir,
		WithPathArgs(GenPaths...),
		WithLangs(GenLangs),
		WithCwd(root),
	)
}

// GenerateOptions 生成参数(functional options)。
type GenerateOptions struct {
	paths   []string // proto 文件/目录(--path,任意写法,内部归一)
	langs   string   // 逗号分隔的语言过滤(go,ts,openapi...)
	cwd     string   // 调用方 cwd(--path 相对路径的基准与外置模式 -o 回指的项目根)
}

// GenOption 生成参数选项。
type GenOption func(*GenerateOptions)

// WithPathArgs 指定 proto 文件/目录(增量生成;不传则全量)。
func WithPathArgs(paths ...string) GenOption {
	return func(o *GenerateOptions) { o.paths = append(o.paths, paths...) }
}

// WithLangs 语言过滤,如 "go,openapi"(空 = 全部 *.gen.yaml)。
func WithLangs(langs string) GenOption {
	return func(o *GenerateOptions) { o.langs = langs }
}

// WithCwd 设置命令运行时的基准目录(默认进程 cwd)。
func WithCwd(dir string) GenOption {
	return func(o *GenerateOptions) { o.cwd = dir }
}

// GenerateFromPath 从指定路径生成 Protobuf 代码(opts 控制增量与语言过滤)。
func GenerateFromPath(ctx context.Context, apiPath string, opts ...GenOption) error {
	o := &GenerateOptions{cwd: "."}
	for _, opt := range opts {
		opt(o)
	}

	// 确保 buf 已安装
	if err := ensureBufInstalled(ctx); err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: %s\033[m\n", err.Error())
		return err
	}

	if !pkg.IsDirExists(apiPath) {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: api directory does not exist: %s\033[m\n", apiPath)
		return fmt.Errorf("api directory does not exist: %s", apiPath)
	}

	if !isBufConfigExists(apiPath) {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: buf config file (%s) does not exist in api directory: %s\033[m\n", defaultBufConfigFile, apiPath)
		return fmt.Errorf("buf config file (%s) does not exist in api directory: %s", defaultBufConfigFile, apiPath)
	}

	if !isBufLockExists(apiPath) {
		fmt.Printf("buf.lock not found in api directory, running `buf dep update`...\n")
		if err := RunBufDepUpdate(ctx, apiPath); err != nil {
			_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: %s\033[m\n", err.Error())
			return err
		}
	}

	fmt.Printf("Generating proto code from YAML files in api directory...\n")

	yamlFiles, err := scanYAMLFiles(apiPath)
	if err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: failed to scan YAML files: %s\033[m\n", err.Error())
		return err
	}

	if len(yamlFiles) == 0 {
		_, _ = fmt.Fprintf(os.Stderr, "\033[33mWARNING: no YAML files found in api directory: %s\033[m\n", apiPath)
		return nil
	}

	fmt.Printf("Running `buf generate` in api directory...\n")
	for _, yamlFile := range yamlFiles {
		if !matchLang(yamlFile, o.langs) {
			continue
		}
		fmt.Printf("Using template file: %s\n", yamlFile)
		if err = RunBufGenerate(ctx, apiPath, yamlFile, o); err != nil {
			// 锁文件过期(依赖声明了但 buf.lock 里没有):自动 dep update 后重试一次
			if strings.Contains(err.Error(), "buf.lock") {
				fmt.Println("buf.lock 过期,执行 `buf dep update` 后重试…")
				if uerr := RunBufDepUpdate(ctx, apiPath); uerr == nil {
					if rerr := RunBufGenerate(ctx, apiPath, yamlFile, o); rerr == nil {
						continue
					} else {
						_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: %s\033[m\n", rerr.Error())
						return rerr
					}
				}
			}
			_, _ = fmt.Fprintf(os.Stderr, "\033[31mERROR: %s\033[m\n", err.Error())
			return err
		}
	}

	fmt.Printf("Protobuf Code generation completed successfully.\n")
	return nil
}

// langByTemplate 语言名 → 模板文件名匹配。
// 规则:go→buf.gen.yaml;其余按 buf.gen.<lang>.yaml。
func matchLang(templateFile, langs string) bool {
	if langs == "" {
		return true
	}
	base := strings.ToLower(filepath.Base(templateFile))
	var want string
	if base == defaultBufGenConfigFile {
		want = "go"
	} else if strings.HasSuffix(base, ".gen.yaml") {
		want = strings.TrimSuffix(strings.TrimSuffix(base, ".yaml"), ".gen")
	}
	for _, l := range strings.Split(langs, ",") {
		if strings.EqualFold(strings.TrimSpace(l), want) {
			return true
		}
	}
	return false
}

// RunBufDepUpdate 在指定目录执行 `buf dep update`，并将输出转发到标准输出/错误。
func RunBufDepUpdate(ctx context.Context, apiPath string) error {
	cmd := exec.CommandContext(ctx, "buf", "dep", "update")
	cmd.Dir = apiPath
	cmd.Env = os.Environ()
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to run `buf dep update`: %w", err)
	}
	return nil
}

// RunBufGenerate 在指定目录执行 `buf generate --template <template>`。
// o 非 nil 时:--path 增量过滤(路径归一为 workspace 相对)+ 外置模式下 Go 生成物用 -o 回指项目。
func RunBufGenerate(ctx context.Context, apiPath, template string, o *GenerateOptions) error {
	if template == "" {
		template = defaultBufGenConfigFile
	}

	args := []string{"generate", "--template", template}

	if o != nil && len(o.paths) > 0 {
		base := o.cwd
		if base == "" {
			base = "."
		}
		workspace := apiPath
		for _, p := range o.paths {
			abs := p
			if !filepath.IsAbs(abs) {
				abs = filepath.Join(base, p)
			}
			rel, err := filepath.Rel(workspace, abs)
			if err != nil || strings.HasPrefix(rel, "..") {
				return fmt.Errorf("--path %s 不在 buf workspace(%s)内", p, workspace)
			}
			rel = filepath.ToSlash(rel)
			if rel == "." {
				continue
			}
			args = append(args, "--path", rel)
		}
	}

	// 外置公共 proto 目录:Go 模板产物用 -o 指回当前项目 api/;其余语言模板
	// 的 out 定义在公共仓自己的 buf.gen 模板里,保持相对其 workspace。
	if o != nil && o.cwd != "" && isTemplateGo(template) {
		if _, projRoot, err := scaffold.Find(); err == nil &&
			filepath.Clean(apiPath) != filepath.Clean(filepath.Join(projRoot, "api")) {
			args = append(args, "-o", filepath.Join(projRoot, "api"))
		}
	}

	cmd := exec.CommandContext(ctx, "buf", args...)
	cmd.Dir = apiPath
	cmd.Env = os.Environ()
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to run `buf generate --template %s`: %w", template, err)
	}
	return nil
}

// isTemplateGo 判断模板是否为 Go 生成模板(名为 buf.gen.yaml)。
func isTemplateGo(template string) bool {
	return filepath.Base(template) == defaultBufGenConfigFile
}

// scanYAMLFiles 仅扫描以 `.gen.yaml` 结尾的文件（忽略大小写），返回匹配的文件路径列表。
func scanYAMLFiles(root string) ([]string, error) {
	var files []string
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			// 访问失败时继续遍历其他路径
			return nil
		}
		if d.IsDir() {
			return nil
		}
		if strings.HasSuffix(strings.ToLower(path), ".gen.yaml") {
			files = append(files, path)
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	return files, nil
}

// checkBuffInstalled 执行 `buf --version` 来探测 buf 是否安装。
// 返回版本字符串（例: "buf v1.0.0"）或执行失败的错误。
func checkBufInstalled(ctx context.Context) (string, error) {
	cmd := exec.CommandContext(ctx, "buf", "--version")
	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("failed to run `buf --version`: %w: %s", err, strings.TrimSpace(string(out)))
	}
	return strings.TrimSpace(string(out)), nil
}

// ensureBufInstalled 检查 buf 是否已安装，若未安装则尝试通过 go install 安装，
// 并在安装后复检其确实可用。安装失败或安装后仍不可用均返回错误。
func ensureBufInstalled(ctx context.Context) error {
	if _, err := checkBufInstalled(ctx); err == nil {
		return nil
	}

	fmt.Println("Installing buf...")

	if err := pkg.GoInstall(ctx, "github.com/bufbuild/buf/cmd/buf@latest"); err != nil {
		return fmt.Errorf("failed to install buf: %w", err)
	}
	if _, err := checkBufInstalled(ctx); err != nil {
		return fmt.Errorf("buf still unavailable after installation: %w", err)
	}
	return nil
}

// isBufLockExists 检查 apiPath 下是否存在 `buf.lock` 文件。
func isBufLockExists(apiPath string) bool {
	path := filepath.Join(apiPath, bufLockFile)
	info, err := os.Stat(path)
	if err != nil {
		return false
	}
	return !info.IsDir()
}

// isBufConfigExists 检查 apiPath 下是否存在 `buf.yaml` 文件。
func isBufConfigExists(apiPath string) bool {
	path := filepath.Join(apiPath, defaultBufConfigFile)
	info, err := os.Stat(path)
	if err != nil {
		return false
	}
	return !info.IsDir()
}
