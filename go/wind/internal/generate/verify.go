package generate

import (
	"bytes"
	"context"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/wind-scaffold/wind/internal/buf"
	"github.com/wind-scaffold/wind/internal/ent"
	"github.com/wind-scaffold/wind/internal/pkg"
	"github.com/wind-scaffold/wind/internal/scaffold"
	"github.com/wind-scaffold/wind/pkg/generators"
)

// ─────────────────────────────────────────────────────────────
// 生成链收口:快照 → 生成 → 校验(gofmt → buf → tidy → build)→ 失败回滚。
// 对应方案 §4.2 第 8 步与验收红线第一条「生成物不改一行即可编译」。
// ─────────────────────────────────────────────────────────────

// affectedRoots 计算本次生成可能触碰的目录(生成前快照、失败回滚的边界)。
func affectedRoots(outputPath, service string, protoOnly bool) []string {
	roots := []string{
		filepath.Join(outputPath, "api", "protos"),
	}
	if !protoOnly {
		roots = append(roots,
			filepath.Join(outputPath, "app", service, "service"),
		)
	}
	return roots
}

// snapshotDirs 递归记录 roots 下全部文件内容;root 不存在时视为空集。
func snapshotDirs(roots []string) (map[string][]byte, error) {
	snap := make(map[string][]byte)
	for _, root := range roots {
		if _, err := os.Stat(root); err != nil {
			continue
		}
		err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() {
				return nil
			}
			data, rerr := os.ReadFile(path)
			if rerr != nil {
				return rerr
			}
			snap[path] = data
			return nil
		})
		if err != nil {
			return nil, err
		}
	}
	return snap, nil
}

// restoreDirs 回滚:快照内容写回;快照中不存在而磁盘上有的文件删除(生成残留)。
func restoreDirs(roots []string, snap map[string][]byte) {
	// 1) 删除新增文件(倒序按路径深度,先文件后可顺势清空目录)
	var added []string
	for _, root := range roots {
		if _, err := os.Stat(root); err != nil {
			continue
		}
		_ = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
			if err != nil || d.IsDir() {
				return nil
			}
			if _, ok := snap[path]; !ok {
				added = append(added, path)
			}
			return nil
		})
	}
	// 深度倒序删除
	for i := 0; i < len(added); i++ {
		for j := i + 1; j < len(added); j++ {
			if len(added[j]) > len(added[i]) {
				added[i], added[j] = added[j], added[i]
			}
		}
	}
	for _, p := range added {
		_ = os.Remove(p)
	}
	// 2) 还原被修改的文件
	for path, data := range snap {
		if cur, err := os.ReadFile(path); err != nil || !bytes.Equal(cur, data) {
			_ = os.MkdirAll(filepath.Dir(path), 0o755)
			_ = os.WriteFile(path, data, 0o644)
		}
	}
}

// verifyChain 生成后校验:ent codegen(ent ORM 时)→ gofmt → buf(仅 go 模板)→ go mod tidy → build。
// 任一步失败返回错误(调用方负责回滚)。
func verifyChain(ctx context.Context, outputPath, service, ormType string) error {
	serviceDir := filepath.Join(outputPath, "app", service, "service")

	// 0) ent codegen:schema 刚由 entimport 生成,没有 runtime 目录就无法编译。
	// 必须走 GenerateService(带 privacy/entql/sql-modifier 等全部 feature,
	// 直调 RunGenerate 会缺 feature 导致生成的 builder 没有 Modify 方法)。
	if strings.EqualFold(strings.TrimSpace(ormType), "ent") {
		if isDir(filepath.Join(serviceDir, "internal", "data", "ent", "schema")) {
			if err := ent.GenerateService(ctx, serviceDir); err != nil {
				return fmt.Errorf("ent codegen: %w", err)
			}
		}
	}

	// 1) gofmt(格式化本次可能触碰的全部 go 文件)
	if err := gofmtDir(serviceDir); err != nil {
		return fmt.Errorf("gofmt: %w", err)
	}

	// 2) buf:proto_dir 来自 .scaffold.yaml(默认 api/protos;外置公共目录跟随),仅跑 go 模板
	cfg, root, err := scaffold.Find()
	if err != nil {
		return fmt.Errorf("解析 .scaffold.yaml: %w", err)
	}
	protoDir := cfg.AbsProtoDir(root)
	genDir := protoDir
	if filepath.Base(protoDir) == "protos" && isDir(genDir) && isBufConfigAt(filepath.Dir(protoDir)) {
		genDir = filepath.Dir(protoDir)
	}
	if err := buf.GenerateFromPath(ctx, genDir, buf.WithCwd(root), buf.WithLangs("go")); err != nil {
		return fmt.Errorf("buf generate: %w", err)
	}

	// 3) 生成代码引用的依赖按 pin 版本先 go get(代理 @latest 会解析到古老的父模块)
	var gets []string
	if strings.EqualFold(strings.TrimSpace(ormType), "gorm") {
		gets = append(gets, generators.GoCrudGorm, generators.GoCrudAPI)
	} else {
		gets = append(gets, generators.GoCrudEntgo, generators.GoCrudAPI)
	}
	for _, g := range gets {
		path, ver := g, ""
		if i := strings.LastIndex(g, " "); i >= 0 {
			path, ver = g[:i], g[i+1:]
		}
		get := exec.CommandContext(ctx, "go", "get", path+"@"+ver)
		get.Dir = outputPath
		get.Env = os.Environ()
		if out, gerr := get.CombinedOutput(); gerr != nil {
			return fmt.Errorf("go get %s: %s", path, strings.TrimSpace(string(out)))
		}
	}

	// 4) go mod tidy
	if err := pkg.GoModTidy(ctx, outputPath); err != nil {
		return fmt.Errorf("go mod tidy: %w", err)
	}

	// 5) 编译目标服务
	build := exec.CommandContext(ctx, "go", "build", fmt.Sprintf("./app/%s/...", service))
	build.Dir = outputPath
	build.Env = os.Environ()
	if out, berr := build.CombinedOutput(); berr != nil {
		return fmt.Errorf("go build: %s", strings.TrimSpace(string(out)))
	}
	return nil
}

func gofmtDir(dir string) error {
	if !isDir(dir) {
		return nil
	}
	var files []string
	err := filepath.WalkDir(dir, func(path string, d fs.DirEntry, err error) error {
		if err == nil && !d.IsDir() && strings.HasSuffix(path, ".go") {
			files = append(files, path)
		}
		return nil
	})
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return nil
	}
	cmd := exec.Command("gofmt", append([]string{"-s", "-w"}, files...)...)
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("%s", strings.TrimSpace(string(out)))
	}
	return nil
}

func isDir(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}

func isBufConfigAt(dir string) bool {
	info, err := os.Stat(filepath.Join(dir, "buf.yaml"))
	return err == nil && !info.IsDir()
}
