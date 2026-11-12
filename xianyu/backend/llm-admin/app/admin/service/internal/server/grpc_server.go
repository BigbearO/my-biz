package server

import (
	grpcx "github.com/go-kratos/kratos/v3/transport/grpc"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	"github.com/xianyu/llm-admin/app/admin/service/internal/service"
	"github.com/xianyu/llm-admin/pkg/bootstrap"

	llmProviderV1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1"
	llmModelV1 "github.com/xianyu/llm-admin/api/gen/go/llm_model/service/v1"
	llmV1 "github.com/xianyu/llm-admin/api/gen/go/llm/service/v1"
)

type GrpcMiddlewares []middleware.Middleware

// NewGrpcMiddleware 创建 gRPC 中间件链:recovery + logging + validate + tracing(鉴权由网关完成)。
func NewGrpcMiddleware(bs *bootstrap.Bootstrap) GrpcMiddlewares {
	return GrpcMiddlewares{
		recovery.Recovery(),
		logging.Server(bs.Logger),
		validate.ProtoValidate(),
		oteltracing.Server(),
	}
}

// NewGrpcServer 创建 gRPC server。
func NewGrpcServer(
	bs *bootstrap.Bootstrap,
	middlewares GrpcMiddlewares,

	// register:param ── 新模块服务形参在此行后注册(make register 工具锚点,勿删)
	llmModelService *service.LlmModelService,
	llmProviderService *service.LlmProviderService,
	llmService *service.LlmService,
) *grpcx.Server {
	srv := grpcx.NewServer(
		grpcx.Address(bs.Cfg.GRPCAddr()),
		grpcx.Middleware(middleware.Chain(middlewares...)),
	)


	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)
	llmModelV1.RegisterLlmModelServiceServer(srv, llmModelService)
	llmProviderV1.RegisterLlmProviderServiceServer(srv, llmProviderService)
	// 内部 gRPC 域:仅供其他后端服务消费(无 REST 路由)
	llmV1.RegisterLlmServiceServer(srv, llmService)

	return srv
}
