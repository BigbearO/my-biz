package main

import (
	"context"

	"github.com/go-kratos/kratos/v3/transport"

	"github.com/wind-scaffold/wind-template/app/sample/service/internal/server"
	"github.com/wind-scaffold/wind-template/app/sample/service/internal/service"
	"github.com/wind-scaffold/wind-template/pkg/bootstrap"
)

// newApp 手写装配:data → service → server 三段自上而下,生成器仅做锚点注入,勿整体重排。
// 退出时 cleanup 按 LIFO 逆序执行。
func newApp(ctx context.Context, bs *bootstrap.Bootstrap) ([]transport.Server, func(), error) {
	var cleanups []func()
	cleanup := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

	// ── data ──
	// ── register:repo ── 新模块仓储在此行后注册(make register 工具锚点,勿删)
	// ── register:client ── 新模块服务客户端在此行后注册(make register 工具锚点,勿删)

	// ── service ──
	greeterSrv := service.NewGreeterService(bs.Logger)
	// ── register:service ── 新模块服务在此行后注册(make register 工具锚点,勿删)

	// ── server ──
	servers := []transport.Server{
		server.NewGRPCServer(bs.Cfg, bs.Logger, greeterSrv),
		server.NewRESTServer(bs.Cfg, bs.Logger, greeterSrv),
	}
	// register:grpc-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)
	// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)

	return servers, cleanup, nil
}
