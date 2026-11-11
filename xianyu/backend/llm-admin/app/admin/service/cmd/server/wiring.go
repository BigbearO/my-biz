package main

import (
	"context"

	"github.com/go-kratos/kratos/v3/transport"
	"github.com/xianyu/llm-admin/app/admin/service/internal/data/client"
	"github.com/xianyu/llm-admin/app/admin/service/internal/pkg/idgen"
	"github.com/xianyu/llm-admin/app/admin/service/internal/server"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data"
	"github.com/xianyu/llm-admin/app/admin/service/internal/service"
	"github.com/xianyu/llm-admin/pkg/bootstrap"
)

// initApp 手写装配整个应用,是本服务的依赖注入点。
// initApp assembles the whole application by hand — the dependency injection point.
//
// 装配严格单向分层,自上而下的阅读顺序即依赖方向:
// The wiring is strictly layered; reading top-down follows the dependency direction:
//
//	基础设施 → 仓储层(data) → 服务层(service) → 传输层(server)
//
// 约定 / Conventions:
//   - 新增模块:在对应分层小节的锚点注释后追加构造行,或由 CRUD 生成器自动注入,
//     并传给下游消费者;漏接由编译器在调用处报错。
//     To add a module: append a constructor line after the anchor comment of its layer
//     section, or let the CRUD generator inject it, then pass it to downstream
//     consumers; a missing connection is a compile error at the call site.
//   - 持有 cleanup 的资源创建成功后立即注册进 cleanups;任何一步失败,rollback 逆序执行已注册
//     的清理(shutdown 与中途失败共用同一条 LIFO 路径)。
//     Resources owning a cleanup register it immediately; rollback runs them LIFO both on
//     mid-way failure and on shutdown.
//   - 本文件只做构造与传参,不写业务逻辑。
//     Construction and parameter passing only; no business logic in this file.

// newApp 手写装配:data → service → server 三段自上而下,生成器仅做锚点注入,勿整体重排。
// 退出时 cleanup 按 LIFO 逆序执行。
func newApp(ctx context.Context, bs *bootstrap.Bootstrap) ([]transport.Server, func(), error) {
	var cleanups []func()
	rollback := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

	// ═══════════════════════ 一、基础设施 ═══════════════════════

	// 雪花 ID 生成器初始化(节点号经 SNOWFLAKE_NODE_ID 注入,缺省用 llm 段 32-63)。
	if _, idErr := idgen.InitFromEnv(); idErr != nil {
		rollback()
		return nil, nil, idErr
	}

	gormClient, cleanupGorm, err := client.NewGormClient(bs)
	if err != nil {
		rollback()
		return nil, nil, err
	}
	cleanups = append(cleanups, cleanupGorm)

	// ═══════════════════════ 二、仓储层(internal/data) ═══════════════════════
	// ── register:repo ── 新模块仓储在此行后注册(make register 工具锚点,勿删)
	llmModelRepo := data.NewLlmModelRepo(bs, gormClient)
	llmProviderRepo := data.NewLlmProviderRepo(bs, gormClient)

	// ═══════════════════════ 三、服务层(internal/service) ═══════════════════════
	// ── register:service ── 新模块服务在此行后注册(make register 工具锚点,勿删)
	llmModelService := service.NewLlmModelService(bs, llmModelRepo)
	llmProviderService := service.NewLlmProviderService(bs, llmProviderRepo)

	// ═══════════════════════ 四、传输层(internal/server) ═══════════════════════

	grpcMiddlewares := server.NewGrpcMiddleware(bs)

	grpcServer := server.NewGrpcServer(bs, grpcMiddlewares,
		// register:grpc-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)
		llmModelService,
		llmProviderService,
	)

	restMiddlewares := server.NewRestMiddleware(bs)

	httpServer := server.NewRestServer(bs, restMiddlewares,
		// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)
		llmModelService,
		llmProviderService,
	)

	return []transport.Server{
		grpcServer,
		httpServer,
	}, rollback, nil
}
