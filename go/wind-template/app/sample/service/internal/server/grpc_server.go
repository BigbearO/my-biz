// Package server 传输层装配:gRPC / REST server 与中间件链。
// 中间件固定四件:recovery + logging + validate + tracing(鉴权由网关完成,此处不做 auth)。
package server

import (
	"log/slog"

	grpcx "github.com/go-kratos/kratos/v3/transport/grpc"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	"github.com/wind-scaffold/wind-template/app/sample/service/internal/service"
	"github.com/wind-scaffold/wind-template/pkg/bootstrap"
	v1 "github.com/wind-scaffold/wind-template/api/gen/go/helloworld/service/v1"
)

// NewGRPCServer 创建 gRPC server。
func NewGRPCServer(cfg *bootstrap.Config, logger *slog.Logger, greeter *service.GreeterService) *grpcx.Server {
	srv := grpcx.NewServer(
		grpcx.Address(cfg.GRPCAddr()),
		grpcx.Middleware(middleware.Chain(
			recovery.Recovery(),
			logging.Server(logger),
			validate.ProtoValidate(),
			oteltracing.Server(),
		)),
	)
	v1.RegisterGreeterServer(srv, greeter)
	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)
	return srv
}
