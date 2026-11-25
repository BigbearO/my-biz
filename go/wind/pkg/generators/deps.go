package generators

import "strings"

// 依赖版本与外部资源常量:生成项目的 go.mod 版本全部出自这里,禁止散落硬编码。
// (对应方案 §4.4 / 设计决策:deps.go 集中治理)

const (
	// KratosV3 kratos 核心与 contrib 均以 v3.0.0-20261026152050 解析。
	KratosV3 = "github.com/go-kratos/kratos/v3"

	// NacosConfigContribPin contrib/config/nacos/v3 的 @latest 在代理上解析异常,必须显式 pin。
	NacosConfigContribPin = "github.com/go-kratos/kratos/contrib/config/nacos/v3 v3.0.0-20260626125723-668db92c2c00"

	// GoCrudEntgo / GoCrudGorm / GoCrudAPI repo 层泛型 CRUD 助手(零 kratos 依赖,已核实)。
	GoCrudEntgo = "github.com/tx7do/go-crud/entgo v0.0.57"
	GoCrudGorm  = "github.com/tx7do/go-crud/gorm v0.0.26"
	GoCrudAPI   = "github.com/tx7do/go-crud/api v0.0.7"

	// TargetGoVersion 生成项目 go.mod 的 go 指令(取 kratos 1.25.0 与 go-crud 1.26.4 的较大值)。
	TargetGoVersion = "1.26.4"

	// GHProxyPrefix GitHub 访问前缀(国内加速);第三方代理存在可用性波动,可整体替换。
	GHProxyPrefix = "https://v6.gh-proxy.org/"

	// DefaultProtoDir proto 源默认位置(与 kratos-template 模板一致)。
	DefaultProtoDir = "api/protos"
)

// ApplyGHProxy 对 github.com 直连 URL 追加代理前缀(幂等)。
// 本地目录与非 github URL 原样返回。
func ApplyGHProxy(repoOrDir string) string {
	const proxyHost = "v6.gh-proxy.org"
	if repoOrDir == "" || strings.Contains(repoOrDir, proxyHost) {
		return repoOrDir
	}
	// 仅处理 URL(本地目录不含 "github.com");git@ 走 ssh 不经过代理,原样返回。
	if strings.HasPrefix(repoOrDir, "git@") || !strings.Contains(repoOrDir, "github.com") {
		return repoOrDir
	}
	if strings.HasPrefix(repoOrDir, "https://github.com") || strings.HasPrefix(repoOrDir, "http://github.com") {
		return GHProxyPrefix + repoOrDir
	}
	return repoOrDir
}
