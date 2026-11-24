package bootstrap

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/go-kratos/kratos/v3/registry"

	nacosreg "github.com/go-kratos/kratos/contrib/registry/nacos/v3"
	nacosv2 "github.com/nacos-group/nacos-sdk-go/v2/clients"
	v2constant "github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	v2vo "github.com/nacos-group/nacos-sdk-go/v2/vo"
)

// newRegistrar 创建 nacos 注册中心(同时可作服务发现用,Registry 实现 Discovery 接口)。
// 创建客户端失败(如 nacos 不可达)返回 nil,由调用方决定以「不注册」模式继续运行。
func newRegistrar(c *Config) (*nacosreg.Registry, error) {
	if c.Nacos.Addr == "" {
		slog.Info("未配置 nacos,服务不注册")
		return nil, nil
	}
	host, port, err := c.Nacos.nacosAddr()
	if err != nil {
		return nil, err
	}
	cli, err := nacosv2.NewNamingClient(v2vo.NacosClientParam{
		ServerConfigs: []v2constant.ServerConfig{{
			IpAddr:      host,
			Port:        port,
			ContextPath: "/nacos",
			Scheme:      "http",
		}},
		ClientConfig: &v2constant.ClientConfig{
			NamespaceId:         c.Nacos.Namespace,
			TimeoutMs:           c.Nacos.timeoutMs(),
			Username:            c.Nacos.Username,
			Password:            c.Nacos.Password,
			LogLevel:            "warn",
			NotLoadCacheAtStart: true,
			UpdateCacheWhenEmpty: false,
		},
	})
	if err != nil {
		return nil, fmt.Errorf("创建 nacos naming 客户端失败: %w", err)
	}
	return nacosreg.New(cli), nil
}

// retryRegistrar 在 kratos Registrar 之上加「启动期有限退避重试」:
// nacos 抖动时按 1s/2s/4s 重试 3 次,最终失败仅记录错误,服务仍以未注册模式对外提供。
type retryRegistrar struct {
	next      registry.Registrar
	attempts  int
	firstWait time.Duration
	onFail    func(err error)
}

func newRetryRegistrar(next registry.Registrar) registry.Registrar {
	return &retryRegistrar{next: next, attempts: 3, firstWait: time.Second}
}

func (r *retryRegistrar) Register(ctx context.Context, si *registry.ServiceInstance) error {
	wait := r.firstWait
	var err error
	for i := 0; i < r.attempts; i++ {
		if err = r.next.Register(ctx, si); err == nil {
			return nil
		}
		slog.Warn("注册到 nacos 失败,退避重试", "attempt", i+1, "wait", wait, "err", err)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(wait):
		}
		wait *= 2
	}
	if r.onFail != nil {
		r.onFail(err)
	}
	return nil // 以未注册模式继续运行
}

func (r *retryRegistrar) Deregister(ctx context.Context, si *registry.ServiceInstance) error {
	wait := r.firstWait
	var err error
	for i := 0; i < r.attempts; i++ {
		if err = r.next.Deregister(ctx, si); err == nil {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(wait):
		}
		wait *= 2
	}
	slog.Error("从 nacos 摘除失败", "err", err)
	return err
}
