package server

import (
	"net/http"

	httpx "github.com/go-kratos/kratos/v3/transport/http"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	"{{.Module}}/app/{{lower .Service}}/service/cmd/server/assets"
	"{{.Module}}/app/{{lower .Service}}/service/internal/service"
	"{{.Module}}/pkg/bootstrap"
{{range $key, $value := .Packages}}
	{{apiPackageAlias (lower $value) $.ApiPackageVersion}} "{{$.Module}}/api/gen/go/{{lower $value}}/service/{{lower $.ApiPackageVersion}}"
{{- end}}
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
{{range $key, $value := .Services}}
	{{camel $key}}Service *service.{{pascal $key}}Service,
{{- end}}
	// register:param ── 新模块服务形参在此行后注册(make register 工具锚点,勿删)
) *httpx.Server {
	srv := httpx.NewServer(
		httpx.Address(bs.Cfg.HTTPAddr()),
		httpx.Middleware(middleware.Chain(middlewares...)),
	)
{{range $key, $value := .Services}}
	{{apiPackageAlias (lower $.Service) $.ApiPackageVersion}}.Register{{pascal $key}}ServiceHTTPServer(srv, {{camel $key}}Service)
{{- end}}

	// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)

	// OpenAPI 文档(由 buf.gen.openapi.yaml 生成后 embed)
	srv.HandleFunc("/q/openapi.yaml", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/yaml")
		_, _ = w.Write(assets.OpenAPI)
	})

	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)

	return srv
}
