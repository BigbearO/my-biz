// Package scaffold 解析项目根的 .scaffold.yaml(module/proto_dir)。
// 所有 proto 相关命令统一从这里解析 proto 源目录(默认 api/protos,可指公共目录)。
package scaffold

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Config .scaffold.yaml 的有效内容。
type Config struct {
	Module   string // go module(缺省取 go.mod)
	ProtoDir string // proto 源目录(相对项目根,或指向公共目录的相对/绝对路径)
}

// Find 自 cwd 向上查找 .scaffold.yaml 与 go.mod,返回解析结果与项目根。
// 优先级:显式 .scaffold.yaml > go.mod module 行 > 空串。
func Find() (*Config, string, error) {
	root, cfgPath, err := findUp()
	if err != nil {
		return nil, "", err
	}

	c := &Config{ProtoDir: defaultProtoDir}
	if cfgPath != "" {
		if err := parseFile(cfgPath, c); err != nil {
			return nil, root, fmt.Errorf("解析 %s 失败: %w", cfgPath, err)
		}
	}
	if c.Module == "" {
		c.Module = readGoModModule(filepath.Join(root, "go.mod"))
	}
	return c, root, nil
}

// AbsProtoDir 把 proto_dir 解析为绝对路径(相对路径以项目根为基准)。
func (c *Config) AbsProtoDir(root string) string {
	if filepath.IsAbs(c.ProtoDir) {
		return filepath.Clean(c.ProtoDir)
	}
	return filepath.Join(root, c.ProtoDir)
}

// IsExternal 判断 proto_dir 是否指向项目根之外(公共 proto 目录模式)。
func (c *Config) IsExternal(root string) bool {
	if filepath.IsAbs(c.ProtoDir) {
		return true
	}
	rel, err := filepath.Rel(root, filepath.Join(root, c.ProtoDir))
	if err != nil {
		return true
	}
	return strings.HasPrefix(rel, "..")
}

// WorkspaceRoot 向上寻找 buf.yaml 所在目录作为 buf workspace 根。
// 找不到时以 protoDir 自身为 workspace 根(外置公共目录自带 buf.yaml 的常规形态)。
// 返回 (workspace 根, protoDir 相对 workspace 的相对路径)。
func (c *Config) WorkspaceRoot(protoDir string) (string, string) {
	dir := protoDir
	for {
		if fileExists(filepath.Join(dir, "buf.yaml")) {
			rel, err := filepath.Rel(dir, protoDir)
			if err != nil || rel == "." {
				rel = ""
			}
			return dir, rel
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}
	return protoDir, ""
}

const defaultProtoDir = "api/protos"

// findUp 自 cwd 向上找 .scaffold.yaml 或 go.mod(任一即视为项目根,优先 .scaffold.yaml)。
func findUp() (root string, scaffoldPath string, err error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", "", err
	}
	start := dir
	for {
		if _, err := os.Stat(filepath.Join(dir, ".scaffold.yaml")); err == nil {
			return dir, filepath.Join(dir, ".scaffold.yaml"), nil
		}
		if _, err := os.Stat(filepath.Join(dir, "go.mod")); err == nil {
			return dir, "", nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}
	return start, "", nil
}

// parseFile 极简逐行解析(只关心 module / proto_dir 两个 key,避免引入 yaml 依赖)。
func parseFile(path string, c *Config) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(strings.SplitN(line, "#", 2)[0])
		if line == "" || strings.HasPrefix(line, "-") {
			continue
		}
		key, val, ok := strings.Cut(line, ":")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		val = strings.Trim(strings.TrimSpace(val), `"'`)
		switch key {
		case "module":
			c.Module = val
		case "proto_dir":
			if val != "" {
				c.ProtoDir = val
			}
		}
	}
	return nil
}

// readGoModModule 读取 go.mod 的 module 行;失败返回空串。
func readGoModModule(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "module ") {
			return strings.TrimSpace(strings.TrimPrefix(line, "module "))
		}
	}
	return ""
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}
