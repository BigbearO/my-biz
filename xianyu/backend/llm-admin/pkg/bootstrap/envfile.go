package bootstrap

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// 多环境配置文件约定(与 springboot application-{profile} 同体验):
//
//	基础文件   <name>.<ext>            如 server.yaml
//	覆盖文件   <name>.<env>.<ext>      如 server.dev.yaml / data.test.yaml,只写差异键
//
// 运行环境由 -e <env>(优先)或 APP_ENV 环境变量指定,二者皆空默认 dev。
// 合并优先级:WIND_* 环境变量 > nacos > 覆盖文件 > 基础文件
// (kratos 的优先级 = source 注册顺序,故必须逐文件 source 且基础在前、覆盖在后;
// 整目录 source 按文件名字典序合并,server.dev.yaml 会先于 server.yaml 合并被反超,不可用)。
//
// 注意:覆盖文件不要写 nacos: 段——配置中心挂载参数由 bootstrap.yaml 预读固化
// (readLocalNacosSection),覆盖文件写了会导致「配置读 A 环境、注册到 B 环境」的分裂;
// nacos 侧环境隔离请用 namespace/dataId。同理不建议在覆盖文件里写 app.env(会压过 -e 选定值)。

// validateEnvironment 校验环境名:仅允许小写字母/数字/-/_。
func validateEnvironment(env string) error {
	for i := 0; i < len(env); i++ {
		c := env[i]
		if c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' {
			continue
		}
		return fmt.Errorf("无效的运行环境 %q:仅允许小写字母/数字/-/_", env)
	}
	return nil
}

// newEnvironmentFilePaths 扫描 confDir,返回(基础文件, 环境覆盖文件)两组路径。
// 覆盖文件匹配 <name>.<env>.<ext> 且 name 去掉 .<env> 后中间不带其他点
// (server.dev.yaml 算,server.test.extra.yaml 不算,避免误伤多段命名)。
// 两组各自保持 os.ReadDir 的字典序;调用方按「基础在前、覆盖在后」拼接。
// 跳过:子目录、"."开头隐藏文件、无扩展名文件。
func newEnvironmentFilePaths(confDir, env string) (base, overlay []string, err error) {
	if err := validateEnvironment(env); err != nil {
		return nil, nil, err
	}
	entries, err := os.ReadDir(confDir)
	if err != nil {
		return nil, nil, fmt.Errorf("扫描配置目录 %q 失败: %w", confDir, err)
	}
	var suffix string
	if env != "" {
		suffix = "." + env
	}
	for _, entry := range entries {
		if entry.IsDir() || strings.HasPrefix(entry.Name(), ".") {
			continue
		}
		ext := filepath.Ext(entry.Name())
		if ext == "" {
			continue
		}
		name := strings.TrimSuffix(entry.Name(), ext)
		path := filepath.Join(confDir, entry.Name())
		if !strings.Contains(name, ".") {
			base = append(base, path)
			continue
		}
		if suffix != "" && strings.HasSuffix(name, suffix) && !strings.Contains(strings.TrimSuffix(name, suffix), ".") {
			overlay = append(overlay, path)
		}
	}
	if len(base)+len(overlay) == 0 {
		return nil, nil, fmt.Errorf("配置目录 %q 中未找到任何配置文件", confDir)
	}
	return base, overlay, nil
}

// overlayDefinesAppEnv 检查任一覆盖文件是否显式定义了 app.env(顶层 app: 下缩进的 env: 键)。
// 与 readLocalNacosSection 同风格的轻量行解析:不用临时 kratos config(其 watcher 关闭时
// 会打 "watcher's ctx cancel" 噪音日志),不引 yaml.v3(保持本包纯标准库依赖)。
// 已知局限:app: 下深层嵌套键(如 app.foo.env:)会误报;按约定覆盖文件不写复杂 app 段,可接受。
func overlayDefinesAppEnv(paths []string) bool {
	for _, p := range paths {
		data, err := os.ReadFile(p)
		if err != nil {
			continue
		}
		inApp := false
		for _, line := range strings.Split(string(data), "\n") {
			trimmed := strings.TrimSpace(strings.SplitN(line, "#", 2)[0])
			if trimmed == "" {
				continue
			}
			indented := strings.HasPrefix(line, " ") || strings.HasPrefix(line, "\t")
			key, _, ok := strings.Cut(trimmed, ":")
			if !ok {
				continue
			}
			key = strings.TrimSpace(key)
			if !indented {
				inApp = key == "app"
				continue
			}
			if inApp && key == "env" {
				return true
			}
		}
	}
	return false
}
