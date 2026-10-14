package main

import (
	"os"

	"github.com/go-kratos/kratos/v2"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	"llm-manage/app/admin/service/internal/data"
	"llm-manage/app/admin/service/internal/data/client"
	"llm-manage/app/admin/service/internal/manager"
	"llm-manage/app/admin/service/internal/server"
	"llm-manage/app/admin/service/internal/service"
	"llm-manage/app/admin/service/internal/utils"
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
func initApp(ctx *bootstrap.Context) (*kratos.App, func(), error) {
	// cleanup 注册表:rollback 时逆序执行。
	// Cleanup registry; rollback runs entries in reverse order.
	var cleanups []func()
	rollback := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

	// ═══════════════════════ 一、基础设施 ═══════════════════════

	// 数据库客户端
	db, dbCleanup, err := client.NewDatabaseClient(ctx)
	if err != nil {
		rollback()
		return nil, func() {}, err
	}
	cleanups = append(cleanups, dbCleanup)

	// 加密器（从环境变量读取密钥，默认用 32 字节密钥）
	encryptKey := os.Getenv("LLM_ENCRYPT_KEY")
	if encryptKey == "" {
		encryptKey = "llllmmmm-llllmmmm-llllmmmm-mmmm-" // 32 字节
	}
	encryptor, err := utils.NewEncryptor(encryptKey)
	if err != nil {
		rollback()
		return nil, func() {}, err
	}

	// ═══════════════════════ 二、仓储层(internal/data) ═══════════════════════

	// 演示模块(helloworld demo):手写仓储,无 CRUD。
	greeterRepo := data.NewGreeterRepo(ctx)

	// LLM 模块仓储
	llmProviderRepo := data.NewLlmProviderRepo(ctx, db)
	llmModelRepo := data.NewLlmModelRepo(ctx, db)

	// ── register:repo ── 新模块仓储在此行后注册(make register 工具锚点,勿删)

	// ═══════════════════════ 三、Manager 层(internal/manager) ═══════════════════════

	// LLM 模块 manager（原子操作/基础设施）
	llmProviderManager := manager.NewLlmProviderManager(ctx, llmProviderRepo, llmModelRepo, encryptor)
	llmModelManager := manager.NewLlmModelManager(ctx, llmModelRepo, llmProviderRepo)

	// ── register:manager ── 新模块 manager 在此行后注册

	// ═══════════════════════ 四、服务层(internal/service) ═══════════════════════

	greeterService := service.NewGreeterService(ctx, greeterRepo)

	// LLM 模块服务（业务编排，依赖 manager）
	llmProviderService := service.NewLlmProviderService(ctx, llmProviderManager)
	llmModelService := service.NewLlmModelService(ctx, llmModelManager)

	// ── register:service ── 新模块服务在此行后注册(make register 工具锚点,勿删)

	// ═══════════════════════ 五、传输层(internal/server) ═══════════════════════

	restMiddlewares := server.NewRestMiddleware(ctx)

	httpServer := server.NewRestServer(ctx, restMiddlewares,
		greeterService,
		llmProviderService,
		llmModelService,
		// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)
	)

	grpcMiddlewares := server.NewGrpcMiddleware(ctx)

	grpcServer := server.NewGrpcServer(ctx, grpcMiddlewares,
		greeterService,
		llmProviderService,
		llmModelService,
		// register:grpc-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)
	)

	return newApp(ctx,
		httpServer,
		grpcServer,
	), rollback, nil
}
