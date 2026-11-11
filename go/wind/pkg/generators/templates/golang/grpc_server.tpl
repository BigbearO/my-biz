package server

import (
	grpcx "github.com/go-kratos/kratos/v3/transport/grpc"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	"{{.Module}}/app/{{lower .Service}}/service/internal/service"
	"{{.Module}}/pkg/bootstrap"
{{range $key, $value := .Packages}}
	{{apiPackageAlias (lower $value) $.ApiPackageVersion}} "{{lower $.Module}}/api/gen/go/{{lower $value}}/service/{{lower $.ApiPackageVersion}}"
{{- end}}
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
{{range $key, $value := .Services}}
	{{camel $key}}Service *service.{{pascal $key}}Service,
{{- end}}
	// register:param ── 新模块服务形参在此行后注册(make register 工具锚点,勿删)
) *grpcx.Server {
	srv := grpcx.NewServer(
		grpcx.Address(bs.Cfg.GRPCAddr()),
		grpcx.Middleware(middleware.Chain(middlewares...)),
	)
{{range $key, $value := .Services}}
	{{apiPackageAlias (lower $value) $.ApiPackageVersion}}.Register{{pascal $key}}ServiceServer(srv, {{camel $key}}Service)
{{- end}}

	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)

	return srv
}
