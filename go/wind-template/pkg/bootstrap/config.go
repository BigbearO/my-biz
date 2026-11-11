// Package bootstrap 是脚手架的运行时胶水:配置加载、日志、链路追踪、nacos 注册与发现。
// 本包随模板复制进每个项目(module 路径随项目替换),不做外部依赖库。
package bootstrap

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/go-kratos/kratos/v3/config"
	"github.com/go-kratos/kratos/v3/config/file"
)

// EnvPrefix 环境变量覆盖前缀,如 WIND_SERVER_HTTP_ADDR。
const EnvPrefix = "WIND_"

// AppInfo 应用标识。
type AppInfo struct {
	Project string `json:"project"`
	AppID   string `json:"appId"`
	Name    string `json:"name"`
	Version string `json:"version"`
	Env     string `json:"env"`
}

// NacosConfig nacos 连接参数(注册与配置中心共用同一地址)。
type NacosConfig struct {
	Addr      string `json:"addr"`      // host:port
	Namespace string `json:"namespace"` // 命名空间 id
	Group     string `json:"group"`     // 配置分组,默认 DEFAULT_GROUP
	DataID    string `json:"dataId"`    // 远端业务配置 dataId(server/data 等)
	TimeoutMs uint64 `json:"timeoutMs"`
	Username  string `json:"username"`
	Password  string `json:"password"`
}

// TraceConfig OTLP 参数,链路与日志共用同一 collector。
type TraceConfig struct {
	Endpoint           string  `json:"endpoint"` // host:port(4317=gRPC,4318=HTTP)
	Exporter           string  `json:"exporter"` // grpc | http(默认 grpc;http 对应 4318)
	Sampler            float64 `json:"sampler"`  // 0~1,默认 1.0
	Insecure           bool    `json:"insecure"`
	EnableTraceContext bool    `json:"enableTraceContext"`
	EnableBaggage      bool    `json:"enableBaggage"`
}

// HTTPExporter 是否走 OTLP HTTP(而非 gRPC)。
func (t *TraceConfig) HTTPExporter() bool {
	return strings.EqualFold(strings.TrimSpace(t.Exporter), "http") ||
		strings.EqualFold(strings.TrimSpace(t.Exporter), "otlp-http")
}

// LogConfig 日志参数。
type LogConfig struct {
	Level string `json:"level"` // debug|info|warn|error,默认 info
}

// ServerConfig 服务监听地址(可由 nacos 下发覆盖)。
type ServerConfig struct {
	GRPC GRPCServerConfig `json:"grpc"`
	HTTP HTTPServerConfig `json:"http"`
}

// DataConfig 数据源配置(data.yaml,可由 nacos 下发覆盖)。
type DataConfig struct {
	Database DatabaseConfig `json:"database"`
	Redis    RedisConfig    `json:"redis"`
}

type DatabaseConfig struct {
	Driver  string `json:"driver"` // mysql | postgres
	Source  string `json:"source"` // DSN
	Migrate bool   `json:"migrate"`
}

type RedisConfig struct {
	Addr     string `json:"addr"`
	Password string `json:"password"`
	DB       int    `json:"db"`
}

type GRPCServerConfig struct {
	Addr string `json:"addr"`
}

type HTTPServerConfig struct {
	Addr string `json:"addr"`
}

// Config 全量配置。
// 合并顺序 file(本地兜底)→ nacos(业务段)→ env(最高优先级);
// 约定:远端 dataId 只放业务配置段(server/data 等),app/nacos/trace/log 始终以本地 bootstrap.yaml 为准。
type Config struct {
	App    AppInfo      `json:"app"`
	Nacos  NacosConfig  `json:"nacos"`
	Trace  TraceConfig  `json:"trace"`
	Log    LogConfig    `json:"log"`
	Server ServerConfig `json:"server"`
	Data   DataConfig   `json:"data"`

	raw config.Config
}

// Raw 返回底层 kratos config,供业务读取额外配置段或做动态 Watch。
func (c *Config) Raw() config.Config { return c.raw }

// GRPCAddr gRPC 监听地址兜底。
func (c *Config) GRPCAddr() string {
	if c.Server.GRPC.Addr == "" {
		return ":9000"
	}
	return c.Server.GRPC.Addr
}

// HTTPAddr HTTP 监听地址兜底。
func (c *Config) HTTPAddr() string {
	if c.Server.HTTP.Addr == "" {
		return ":8000"
	}
	return c.Server.HTTP.Addr
}

// ServiceName 注册到 nacos 的服务名:project/appId。
func (c *Config) ServiceName() string {
	name := c.App.Name
	if name == "" {
		name = c.App.AppID
	}
	if c.App.Project != "" {
		return c.App.Project + "/" + name
	}
	return name
}

func (c *NacosConfig) nacosAddr() (string, uint64, error) {
	host, portStr, err := net.SplitHostPort(strings.TrimSpace(c.Addr))
	if err != nil {
		return "", 0, fmt.Errorf("nacos 地址需为 host:port,当前 %q: %w", c.Addr, err)
	}
	port, err := strconv.ParseUint(portStr, 10, 64)
	if err != nil {
		return "", 0, fmt.Errorf("nacos 端口非法 %q: %w", portStr, err)
	}
	return host, port, nil
}

func (c *NacosConfig) group() string {
	if c.Group == "" {
		return "DEFAULT_GROUP"
	}
	return c.Group
}

func (c *NacosConfig) timeoutMs() uint64 {
	if c.TimeoutMs == 0 {
		return 5000
	}
	return c.TimeoutMs
}

func (t *TraceConfig) sampler() float64 {
	if t.Sampler <= 0 || t.Sampler > 1 {
		return 1.0
	}
	return t.Sampler
}

// loadConfig 加载配置:本地 yaml 兜底 + nacos 远端覆盖(失败仅告警)+ env 最高优先级。
// nacos 不可达不阻塞启动。返回的 close 释放全部配置资源。
func loadConfig(ctx context.Context, confDir string) (*Config, func(), error) {
	// 1) 预读本地 bootstrap.yaml 的 nacos 连接段。
	// 用轻量文件解析而非 kratos config:预读的临时 config 关闭时其 file watcher
	// 会打 "watcher's ctx cancel" INFO 噪音日志(预读与正式加载各一次)。
	preNacos := readLocalNacosSection(filepath.Join(confDir, "bootstrap.yaml"))

	// 2) 组装最终 sources:file(本地兜底)→ nacos(业务段覆盖)→ env(最高优先级)
	sources := []config.Source{file.NewSource(confDir)}
	if preNacos != nil && preNacos.Addr != "" {
		src, err := newNacosConfigSource(ctx, preNacos)
		if err != nil {
			slog.Warn("nacos 配置中心不可用,使用本地配置兜底", "err", err)
		} else {
			sources = append(sources, src)
		}
	}
	sources = append(sources, newEnvSource(EnvPrefix))

	cfg := config.New(config.WithSource(sources...))
	if err := cfg.Load(); err != nil {
		return nil, nil, fmt.Errorf("加载配置失败: %w", err)
	}

	c := &Config{}
	if err := cfg.Scan(c); err != nil {
		_ = cfg.Close()
		return nil, nil, fmt.Errorf("解析配置失败: %w", err)
	}
	return c, func() { _ = cfg.Close() }, nil
}

// readLocalNacosSection 轻量解析 bootstrap.yaml 的 nacos 段(仅取连接所需键;失败返回 nil 走本地兜底)。
func readLocalNacosSection(path string) *NacosConfig {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	nc := &NacosConfig{Group: "DEFAULT_GROUP"}
	inNacos := false
	for _, line := range strings.Split(string(data), "\n") {
		trimmed := strings.TrimSpace(strings.SplitN(line, "#", 2)[0])
		if trimmed == "" {
			continue
		}
		indented := strings.HasPrefix(line, " ") || strings.HasPrefix(line, "\t")
		key, val, ok := strings.Cut(trimmed, ":")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		val = strings.Trim(strings.TrimSpace(val), `"'`)
		if !indented {
			inNacos = key == "nacos"
			continue
		}
		if !inNacos {
			continue
		}
		switch key {
		case "addr":
			nc.Addr = val
		case "namespace":
			nc.Namespace = val
		case "group":
			nc.Group = val
		case "dataId":
			nc.DataID = val
		case "timeoutMs":
			if v, perr := strconv.ParseUint(val, 10, 64); perr == nil {
				nc.TimeoutMs = v
			}
		case "username":
			nc.Username = val
		case "password":
			nc.Password = val
		}
	}
	if nc.Addr == "" {
		return nil
	}
	return nc
}

// newNacosConfigSource 创建 nacos 配置源(v2 SDK,gRPC 拉取);cfg 为 nil 时返回错误(首次预读阶段)。
func newNacosConfigSource(ctx context.Context, nc *NacosConfig) (config.Source, error) {
	if nc == nil || nc.Addr == "" {
		return nil, fmt.Errorf("nacos 未配置")
	}
	cli, err := newNacosV2ConfigClient(nc)
	if err != nil {
		return nil, fmt.Errorf("创建 nacos 配置客户端失败: %w", err)
	}
	_ = ctx
	return &fallbackSource{
		next: &nacosV2Source{cli: cli, group: nc.group(), dataID: nc.DataID},
		name: "nacos:" + nc.DataID,
	}, nil
}

// fallbackSource 容错配置源:远端拉取失败时告警并返回空集,
// 保证「nacos 不可达 → 本地配置兜底启动」(kratos 的 Load 任一 source 失败即整体失败,故必须包这层)。
type fallbackSource struct {
	next config.Source
	name string
}

func (s *fallbackSource) Load() ([]*config.KeyValue, error) {
	kvs, err := s.next.Load()
	if err != nil {
		slog.Warn("远端配置拉取失败,使用本地配置兜底", "source", s.name, "err", err)
		return nil, nil
	}
	return kvs, nil
}

func (s *fallbackSource) Watch() (config.Watcher, error) { return s.next.Watch() }
func (s *fallbackSource) String() string                 { return s.name }
