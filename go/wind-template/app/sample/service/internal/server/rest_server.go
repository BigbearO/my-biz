package server

import (
	"log/slog"
	"net/http"

	httpx "github.com/go-kratos/kratos/v3/transport/http"

	"github.com/go-kratos/kratos/contrib/middleware/validate/v3"
	oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"
	"github.com/go-kratos/kratos/v3/middleware"
	"github.com/go-kratos/kratos/v3/middleware/logging"
	"github.com/go-kratos/kratos/v3/middleware/recovery"

	v1 "github.com/wind-scaffold/wind-template/api/gen/go/helloworld/service/v1"
	"github.com/wind-scaffold/wind-template/app/sample/service/cmd/server/assets"
	"github.com/wind-scaffold/wind-template/app/sample/service/internal/service"
	"github.com/wind-scaffold/wind-template/pkg/bootstrap"
)

// NewRESTServer 创建 HTTP server(路由由 proto 的 google.api.http 注解生成)。
func NewRESTServer(cfg *bootstrap.Config, logger *slog.Logger, greeter *service.GreeterService) *httpx.Server {
	srv := httpx.NewServer(
		httpx.Address(cfg.HTTPAddr()),
		httpx.Middleware(middleware.Chain(
			recovery.Recovery(),
			logging.Server(logger),
			validate.ProtoValidate(),
			oteltracing.Server(),
		)),
	)
	v1.RegisterGreeterHTTPServer(srv, greeter)
	// register:rest-arg ── 新模块服务实参在此行后追加(make register 工具锚点,勿删)

	// OpenAPI 文档(由 buf.gen.openapi.yaml 生成后 embed)
	srv.HandleFunc("/q/openapi.yaml", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/yaml")
		_, _ = w.Write(assets.OpenAPI)
	})
	// register:route ── 新模块路由在此行后注册(make register 工具锚点,勿删)
	return srv
}
