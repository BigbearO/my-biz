package handler

import (
	"logViewer/gozero/internal/svc"
	"net/http"

	"github.com/zeromicro/go-zero/rest"
)

func RegisterHandlers(engine *rest.Server, svcCtx *svc.ServiceContext) {
	// 日志相关路由
	engine.AddRoute(rest.Route{
		Method:  http.MethodPost,
		Path:    "/log/view",
		Handler: NewSearchLogsHandler(svcCtx),
	})

	// 健康检查路由
	engine.AddRoute(rest.Route{
		Method:  http.MethodGet,
		Path:    "/log/health",
		Handler: NewHealthHandler(),
	})

}
