package server

import (
	"github.com/go-kratos/kratos/v2/middleware"
	"github.com/go-kratos/kratos/v2/transport/http"

	swaggerUI "github.com/tx7do/kratos-swagger-ui"

	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"github.com/tx7do/kratos-bootstrap/rpc"

	"bear-admin/app/ai/service/cmd/server/assets"
	"bear-admin/app/ai/service/internal/service"

	"bigbear.com/xxs-common/common-auth-go"
	"bigbear.com/xxs-common/xxs-otel-log/logx"

	aipb "bear-admin/api/gen/go/ai/service/v1"
)

type RestMiddlewares []middleware.Middleware

func NewRestMiddleware(ctx *bootstrap.Context) RestMiddlewares {
	var ms []middleware.Middleware
	ms = append(ms, logx.TracedLoggingServer(logx.TracedLogger(logx.TeeLogger(ctx.GetLogger()))))
	ms = append(ms, auth.HTTPServerMiddleware())

	return ms
}

func NewRestServer(
	ctx *bootstrap.Context,

	middlewares RestMiddlewares,

	aiService *service.AiService,
) *http.Server {
	cfg := ctx.GetConfig()

	if cfg == nil || cfg.Server == nil || cfg.Server.Rest == nil {
		return nil
	}

	srv, err := rpc.CreateRestServer(cfg, middlewares...)
	if err != nil {
		panic(err)
	}

	if cfg.GetServer().GetRest().GetEnableSwagger() {
		swaggerUI.RegisterSwaggerUIServerWithOption(
			srv,
			swaggerUI.WithTitle("GoWind AI"),
			swaggerUI.WithMemoryData(assets.OpenApiData, "yaml"),
		)
	}

	srv.HandleFunc("ai/v1/chat/stream", service.HandleChatStream)

	aipb.RegisterChatServiceHTTPServer(srv, aiService)
	aipb.RegisterHelloServiceHTTPServer(srv, aiService)

	return srv
}
