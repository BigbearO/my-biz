package doctor

import (
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/wind-scaffold/wind/internal/scaffold"
)

// runProjectChecks 项目级体检:仅在项目内(能找到 .scaffold.yaml 或 go.mod)运行。
// 返回失败/警告计数。netCheck 为真时额外探测 bootstrap.yaml 里的 nacos/OTLP 端点。
func runProjectChecks(out io.Writer, netCheck bool) int {
	cfg, root, err := scaffold.Find()
	if err != nil {
		return 0
	}
	failed := 0

	fmt.Fprintln(out, "\n\033[36m[project]\033[m", root)

	// 1) proto_dir 有效性
	protoDir := cfg.AbsProtoDir(root)
	if !dirExists(protoDir) {
		failed++
		fmt.Fprintf(out, "  \033[31mFAIL\033[m  proto_dir 不存在: %s(检查 .scaffold.yaml)\n", protoDir)
	} else {
		fmt.Fprintf(out, "  \033[32mOK\033[m    proto_dir: %s\n", protoDir)
	}

	// 2) 依赖图无 kratos/v2(go.mod 直接依赖即可判;indirect 由 tidy 兜底)
	if gomod := filepath.Join(root, "go.mod"); fileExists(gomod) {
		if strings.Contains(readFileString(gomod), "github.com/go-kratos/kratos/v2") {
			failed++
			fmt.Fprintf(out, "  \033[31mFAIL\033[m  go.mod 含 go-kratos/kratos/v2(v2/v3 同图会编译冲突)\n")
		} else {
			fmt.Fprintf(out, "  \033[32mOK\033[m    依赖图无 kratos/v2\n")
		}
	}

	// 3) go-crud 分层铁律:仅允许 internal/data/** import 仓储包(entgo/gorm)
	violations := scanGoCrudLayering(root)
	if len(violations) > 0 {
		failed++
		for _, v := range violations {
			fmt.Fprintf(out, "  \033[31mFAIL\033[m  go-crud 越层引用: %s(只允许 internal/data/**)\n", v)
		}
	} else if appDir := filepath.Join(root, "app"); dirExists(appDir) {
		fmt.Fprintf(out, "  \033[32mOK\033[m    go-crud 分层铁律\n")
	}

	// 4) --net:nacos / OTLP 端点连通性
	if netCheck {
		failed += netChecks(out, root)
	}

	return failed
}

// scanGoCrudLayering 扫描 app/**/internal 下非 data 层的 go-crud 仓储包引用。
func scanGoCrudLayering(root string) []string {
	var violations []string
	appDir := filepath.Join(root, "app")
	if !dirExists(appDir) {
		return nil
	}
	_ = filepath.WalkDir(appDir, func(path string, d os.DirEntry, err error) error {
		if err != nil || d.IsDir() || !strings.HasSuffix(path, ".go") {
			return nil
		}
		normalized := filepath.ToSlash(path)
		if strings.Contains(normalized, "/internal/data/") {
			return nil
		}
		data, rerr := os.ReadFile(path)
		if rerr != nil {
			return nil
		}
		text := string(data)
		if strings.Contains(text, "go-crud/entgo") || strings.Contains(text, "go-crud/gorm") {
			violations = append(violations, normalized)
		}
		return nil
	})
	return violations
}

// netChecks 从 configs/bootstrap.yaml 解析 nacos 与 OTLP 地址做 TCP 拨号(2s 超时)。
func netChecks(out io.Writer, root string) int {
	failed := 0
	bootstrap := firstBootstrapYaml(root)
	if bootstrap == "" {
		fmt.Fprintln(out, "  \033[33mSKIP\033[m  未找到 configs/bootstrap.yaml,跳过连通性探测")
		return 0
	}
	content := readFileString(bootstrap)
	nacos := extractAddr(content, "nacos")
	otlp := extractAddr(content, "trace")
	for name, addr := range map[string]string{"nacos": nacos, "otlp": otlp} {
		if addr == "" {
			continue
		}
		conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
		if err != nil {
			failed++
			fmt.Fprintf(out, "  \033[33mWARN\033[m  %s(%s)不可达: %v(开发期可本地兜底,联调前需启动)\n", name, addr, err)
			continue
		}
		_ = conn.Close()
		fmt.Fprintf(out, "  \033[32mOK\033[m    %s(%s)可达\n", name, addr)
	}
	return failed
}

// ── 小工具 ──

func dirExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func readFileString(path string) string {
	b, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	return string(b)
}

// firstBootstrapYaml 在项目内找一个服务的 bootstrap.yaml(app/*/service/configs/bootstrap.yaml)。
func firstBootstrapYaml(root string) string {
	matches, _ := filepath.Glob(filepath.Join(root, "app", "*", "service", "configs", "bootstrap.yaml"))
	if len(matches) > 0 {
		return matches[0]
	}
	return ""
}

// extractAddr 从 yaml 文本中截取指定段落下的首个 addr/endpoint(host:port)。
func extractAddr(content, section string) string {
	lines := strings.Split(content, "\n")
	inSection := false
	for _, line := range lines {
		trimmed := strings.TrimSpace(strings.SplitN(line, "#", 2)[0])
		if trimmed == "" {
			continue
		}
		if !strings.HasPrefix(line, " ") && !strings.HasPrefix(line, "\t") {
			inSection = strings.HasPrefix(trimmed, section+":")
			continue
		}
		if !inSection {
			continue
		}
		for _, key := range []string{"addr:", "endpoint:"} {
			if strings.HasPrefix(trimmed, key) {
				val := strings.Trim(strings.TrimSpace(strings.TrimPrefix(trimmed, key)), `"'`)
				if val != "" && strings.Contains(val, ":") && !strings.HasPrefix(val, "${") {
					return val
				}
			}
		}
	}
	return ""
}
