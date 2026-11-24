package bootstrap

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestValidateEnvironment(t *testing.T) {
	for _, env := range []string{"dev", "test", "prod", "prod-cn", "uat_2", "x1-y_z"} {
		if err := validateEnvironment(env); err != nil {
			t.Errorf("validateEnvironment(%q) = %v, want nil", env, err)
		}
	}
	for _, env := range []string{"Dev", "PROD", "prod x", "dev/test", "中文", "dev.", "dev*"} {
		if err := validateEnvironment(env); err == nil {
			t.Errorf("validateEnvironment(%q) = nil, want error", env)
		}
	}
}

// writeConfigDir 构造一个覆盖各种文件形态的临时配置目录,返回目录路径。
func writeConfigDir(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	files := []string{
		"bootstrap.yaml",
		"data.yaml",
		"server.yaml",
		"server.dev.yaml", // dev 覆盖
		"data.dev.yaml",
		"server.test.yaml", // test 覆盖
		"data.test.yaml",
		"server.test.extra.yaml", // 中间多点:不算覆盖
		"bootstrap.dev.yaml",     // 引导文件的 dev 覆盖(合法,trace/log 差异可放)
		".hidden.yaml",           // 隐藏文件:跳过
		"noext",                  // 无扩展名:跳过
	}
	for _, f := range files {
		if err := os.WriteFile(filepath.Join(dir, f), []byte("{}\n"), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}

func TestNewEnvironmentFilePaths(t *testing.T) {
	dir := writeConfigDir(t)

	// dev:基础 3 文件;bootstrap.dev.yaml 也按 <base>.<env> 规则归入覆盖组(os.ReadDir 字典序)
	base, overlay, err := newEnvironmentFilePaths(dir, "dev")
	if err != nil {
		t.Fatalf("dev: %v", err)
	}
	wantBase := []string{"bootstrap.yaml", "data.yaml", "server.yaml"}
	wantOverlay := []string{"bootstrap.dev.yaml", "data.dev.yaml", "server.dev.yaml"}
	if !equalRelPaths(base, dir, wantBase) || !equalRelPaths(overlay, dir, wantOverlay) {
		t.Errorf("dev: base=%v overlay=%v, want base=%v overlay=%v", relAll(base, dir), relAll(overlay, dir), wantBase, wantOverlay)
	}

	// test:server.test.extra.yaml 不算覆盖
	_, overlayTest, err := newEnvironmentFilePaths(dir, "test")
	if err != nil {
		t.Fatalf("test: %v", err)
	}
	wantOverlayTest := []string{"data.test.yaml", "server.test.yaml"}
	if !equalRelPaths(overlayTest, dir, wantOverlayTest) {
		t.Errorf("test: overlay=%v, want %v", relAll(overlayTest, dir), wantOverlayTest)
	}

	// prod:无覆盖文件,基础照常返回
	baseProd, overlayProd, err := newEnvironmentFilePaths(dir, "prod")
	if err != nil {
		t.Fatalf("prod: %v", err)
	}
	if len(overlayProd) != 0 {
		t.Errorf("prod: overlay=%v, want empty", relAll(overlayProd, dir))
	}
	if !equalRelPaths(baseProd, dir, wantBase) {
		t.Errorf("prod: base=%v, want %v", relAll(baseProd, dir), wantBase)
	}

	// 非法环境名:启动即失败
	if _, _, err := newEnvironmentFilePaths(dir, "Dev"); err == nil {
		t.Error("env=Dev: want error, got nil")
	}

	// 不存在的目录
	if _, _, err := newEnvironmentFilePaths(filepath.Join(dir, "missing"), "dev"); err == nil {
		t.Error("missing dir: want error, got nil")
	}
}

func TestOverlayDefinesAppEnv(t *testing.T) {
	dir := t.TempDir()
	without := filepath.Join(dir, "server.dev.yaml")
	if err := os.WriteFile(without, []byte("server:\n  http:\n    addr: \":8000\"\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if overlayDefinesAppEnv([]string{without}) {
		t.Error("no app section: want false")
	}

	with := filepath.Join(dir, "data.dev.yaml")
	if err := os.WriteFile(with, []byte("# 注释里 app:\n#   env: xxx 不算\ndata:\n  database:\n    source: dsn\napp:\n  env: uat # 命中\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if !overlayDefinesAppEnv([]string{without, with}) {
		t.Error("overlay defines app.env: want true")
	}

	// env 键不在 app: 段下(如顶层 env: 段或 nacos: 段)不算
	other := filepath.Join(dir, "x.dev.yaml")
	if err := os.WriteFile(other, []byte("env:\n  host: 10.0.0.5\nnacos:\n  env: y\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if overlayDefinesAppEnv([]string{other}) {
		t.Error("env outside app section: want false")
	}
}

func relAll(paths []string, dir string) []string {
	out := make([]string, 0, len(paths))
	for _, p := range paths {
		rel, err := filepath.Rel(dir, p)
		if err != nil {
			out = append(out, p)
			continue
		}
		out = append(out, filepath.ToSlash(rel))
	}
	return out
}

func equalRelPaths(got []string, dir string, wantSlash []string) bool {
	g := relAll(got, dir)
	if len(g) != len(wantSlash) {
		return false
	}
	for i := range g {
		if g[i] != wantSlash[i] || strings.ContainsRune(g[i], '\\') {
			return false
		}
	}
	return true
}
