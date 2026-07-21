package server

import (
	"github.com/go-kratos/kratos/v2/middleware"
	"github.com/go-kratos/kratos/v2/middleware/logging"
	"github.com/go-kratos/kratos/v2/transport/grpc"

	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"github.com/tx7do/kratos-bootstrap/rpc"

	"bear-admin/app/ai/service/internal/service"

	"bigbear.com/xxs-common/common-auth-go"

	aipb "bear-admin/api/gen/go/ai/service/v1"
	userpb "bear-admin/api/gen/go/user/v1"
)

type GrpcMiddlewares []middleware.Middleware

func NewGrpcMiddleware(ctx *bootstrap.Context) GrpcMiddlewares {
	var ms []middleware.Middleware
	ms = append(ms, logging.Server(ctx.GetLogger()))
	ms = append(ms, auth.GrpcServerMiddleware())
	return ms
}

func NewGrpcServer(
	ctx *bootstrap.Context,

	middlewares GrpcMiddlewares,

	aiService *service.AiService,
	userService *service.UserService,
) *grpc.Server {
	cfg := ctx.GetConfig()

	if cfg == nil || cfg.Server == nil || cfg.Server.Grpc == nil {
		return nil
	}

	srv, err := rpc.CreateGrpcServer(cfg, middlewares...)
	if err != nil {
		panic(err)
	}

	aipb.RegisterChatServiceServer(srv, aiService)
	aipb.RegisterHelloServiceServer(srv, aiService)
	userpb.RegisterUserServiceServer(srv, userService)

	return srv
}
