package bootstrap

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"

	kratos "github.com/go-kratos/kratos/v3"
	"github.com/go-kratos/kratos/v3/log"
	"github.com/go-kratos/kratos/v3/registry"
	"github.com/go-kratos/kratos/v3/transport"
)

// InitAppFunc 由各服务的 cmd/server/wiring.go 提供:装配全部 transport.Server。
// 返回的 cleanup 在进程退出时逆序执行。
type InitAppFunc func(ctx context.Context, bs *Bootstrap) ([]transport.Server, func(), error)

// RunOption 定制 Run 行为。
type RunOption func(*runOptions)

type runOptions struct {
	version string
}

// WithVersion 用 ldflags 注入的版本号覆盖配置里的 version(main.go 的 var version)。
func WithVersion(v string) RunOption {
	return func(o *runOptions) { o.version = v }
}

// Bootstrap 运行时句柄:装配完成后交给 InitAppFunc 使用。
type Bootstrap struct {
	Cfg       *Config
	Logger    *slog.Logger
	Registrar registry.Registrar // 可能为 nil(nacos 不可达时以不注册模式运行)
	Discovery registry.Discovery // 与 Registrar 同源(nacos Registry 同时实现两者)
}

// Run 应用入口:配置 → 日志 → 链路 → 注册 → initApp → 运行 → 优雅退出。
// confDir 为本地配置目录(通常 ./configs)。
func Run(ctx context.Context, confDir string, initApp InitAppFunc, opts ...RunOption) (err error) {
	// -conf 命令行覆盖配置目录
	flag.StringVar(&confDir, "conf", confDir, "配置目录路径")
	flag.Parse()

	var ro runOptions
	for _, o := range opts {
		o(&ro)
	}

	cfg, closeCfg, err := loadConfig(ctx, confDir)
	if err != nil {
		return err
	}
	defer closeCfg()

	if ro.version != "" {
		cfg.App.Version = ro.version
	}
	if cfg.App.Version == "" {
		cfg.App.Version = "dev"
	}
	if cfg.App.Env == "" {
		cfg.App.Env = "dev"
	}
	// 环境变量兜底版本号(app.mk build 时经 .env 注入)
	if v := os.Getenv("SERVICE_APP_VERSION"); v != "" && cfg.App.Version == "dev" {
		cfg.App.Version = v
	}

	// logger:stdout + OTLP 双路,先建(后续所有日志都走它)
	logger, closeLogger, err := newLogger(ctx, cfg, cfg.Trace.Endpoint, cfg.Trace.Insecure)
	if err != nil {
		return fmt.Errorf("初始化日志失败: %w", err)
	}
	log.SetDefault(logger)
	slog.SetDefault(logger)

	// tracer:全局 Provider + Propagator
	_, closeTracer, err := newTracerProvider(ctx, cfg)
	if err != nil {
		return fmt.Errorf("初始化链路追踪失败: %w", err)
	}

	// registrar:nacos,失败降级为不注册
	reg, err := newRegistrar(cfg)
	if err != nil {
		slog.Error("创建注册中心失败,以不注册模式运行", "err", err)
		reg = nil
	}

	bs := &Bootstrap{
		Cfg:       cfg,
		Logger:    logger,
		Registrar: reg,
	}
	if reg != nil {
		bs.Discovery = reg // nacos Registry 同时实现 registry.Discovery
	}

	// 业务装配
	servers, cleanup, err := initApp(ctx, bs)
	if err != nil {
		logger.Error("初始化应用失败", "err", err)
		return err
	}
	defer cleanup()

	appOpts := []kratos.Option{
		kratos.Name(cfg.ServiceName()),
		kratos.Version(cfg.App.Version),
		kratos.ID(instanceID()),
		kratos.Logger(logger),
		kratos.Server(servers...),
		// 退出顺序:BeforeStop(链路 flush → 日志 flush)→ 摘除注册 → server 停止
		kratos.BeforeStop(func(context.Context) error {
			if closeTracer != nil {
				closeTracer()
			}
			closeLogger()
			return nil
		}),
	}
	if reg != nil {
		appOpts = append(appOpts, kratos.Registrar(newRetryRegistrar(reg)))
	}

	app := kratos.New(appOpts...)
	if err := app.Run(); err != nil && !errors.Is(err, context.Canceled) {
		return err
	}
	return nil
}
