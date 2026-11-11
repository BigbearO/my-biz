package server

import (
	"net/http"

	httpx "github.com/go-kratos/kratos/v3/transport/http"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	"github.com/xianyu/llm-admin/app/admin/service/cmd/server/assets"
	"github.com/xianyu/llm-admin/app/admin/service/internal/service"
	"github.com/xianyu/llm-admin/pkg/bootstrap"
	llmProviderV1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1"
	llmModelV1 "github.com/xianyu/llm-admin/api/gen/go/llm_model/service/v1"
)

type RestMiddlewares []middleware.Middleware

// NewRestMiddleware 创建 HTTP 中间件链:recovery + logging + validate + tracing(鉴权由网关完成)。
func NewRestMiddleware(bs *bootstrap.Bootstrap) RestMiddlewares {
	return RestMiddlewares{
		recovery.Recovery(),
		logging.Server(bs.Logger),
		validate.ProtoValidate(),
		oteltracing.Server(),
	}
}

// NewRestServer 创建 HTTP server(路由由 proto 的 google.api.http 注解生成)。
func NewRestServer(
	bs *bootstrap.Bootstrap,
	middlewares RestMiddlewares,

	// register:param ── 新模块服务形参在此行后注册(make register 工具锚点,勿删)
	llmModelService *service.LlmModelService,
	llmProviderService *service.LlmProviderService,
) *httpx.Server {
	srv := httpx.NewServer(
		httpx.Address(bs.Cfg.HTTPAddr()),
		httpx.Middleware(middleware.Chain(middlewares...)),
	)


	// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)

	// OpenAPI 文档(由 buf.gen.openapi.yaml 生成后 embed)
	srv.HandleFunc("/q/openapi.yaml", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/yaml")
		_, _ = w.Write(assets.OpenAPI)
	})

	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)
	llmModelV1.RegisterLlmModelServiceHTTPServer(srv, llmModelService)
	llmProviderV1.RegisterLlmProviderServiceHTTPServer(srv, llmProviderService)

	return srv
}
