package project

import (
	"context"
	"fmt"
	"log"
	"path/filepath"

	"github.com/wind-scaffold/wind/internal/pkg"
)

const (
	TemplateModuleName = "github.com/wind-scaffold/wind-template"
)

type Project struct {
	Name   string
	Path   string
	Module string
}

// New a project from a git repo, or from a local template directory when
// layout points at one (免 clone,替换机制与仓库路径完全一致).
func (p *Project) New(ctx context.Context, dir string, layout string, branch string) error {
	to := filepath.Join(dir, p.Name)

	if isLocalTemplateDir(layout) {
		if err := pkg.CopyLocalTemplate(layout, to, p.Name, []string{".git", ".github", "api/gen"}); err != nil {
			return err
		}
	} else {
		repo := pkg.NewRepo(layout, branch)
		if err := repo.CopyTo(ctx, to, p.Name, []string{".git", ".github", "api/gen"}); err != nil {
			return err
		}
	}

	// ⚠️ 只允许在新项目目录内做模块名替换——原 gowind 实现传的是 dir(CWD),
	// 会把 CWD 下所有含模板模块路径的文件(包括模板仓与 CLI 源码)一起改写。
	updateCount, err := pkg.ReplaceTemplateInCurrentDir(to, TemplateModuleName, p.Module)
	if err != nil {
		return err
	}
	log.Printf("Updated %d files.\n", updateCount)

	return nil
}

var repoAddIgnores = []string{
	".git", ".github", "api", "README.md", "LICENSE", "go.mod", "go.sum", "third_party", "openapi.yaml", ".gitignore",
}

func (p *Project) Add(ctx context.Context, dir string, layout string, branch string, mod string, pkgPath string) error {
	to := filepath.Join(dir, p.Name)

	log.Printf("🚀 Add service %s, layout repo is %s, please wait a moment.\n\n", p.Name, layout)

	pkgPath = fmt.Sprintf("%s/%s", mod, pkgPath)
	if isLocalTemplateDir(layout) {
		if err := pkg.CopyLocalTemplateV2(layout, to, pkgPath, repoAddIgnores,
			[]string{filepath.Join(p.Path, "api"), "api"}); err != nil {
			return err
		}
	} else {
		repo := pkg.NewRepo(layout, branch)
		err := repo.CopyToV2(ctx, to, pkgPath, repoAddIgnores, []string{filepath.Join(p.Path, "api"), "api"})
		if err != nil {
			return err
		}
	}

	// 同 New():替换只作用于本次并入的子树,不碰项目里其他内容。
	updateCount, err := pkg.ReplaceTemplateInCurrentDir(to, TemplateModuleName, p.Module)
	if err != nil {
		return err
	}
	log.Printf("Updated %d files.\n", updateCount)

	return nil
}
