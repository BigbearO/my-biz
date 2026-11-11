package main

import (
	"context"

	"github.com/go-kratos/kratos/v3/transport"

{{- if .ImportData}}
	"{{.Module}}/app/{{lower .Service}}/service/internal/data"
{{- end}}
{{- if .ImportClient}}
	"{{.Module}}/app/{{lower .Service}}/service/internal/data/client"
{{- end}}
{{- if .ImportServer}}
	"{{.Module}}/app/{{lower .Service}}/service/internal/server"
{{- end}}
{{- if .ImportServicePkg}}
	"{{.Module}}/app/{{lower .Service}}/service/internal/service"
{{- end}}

	"{{.Module}}/pkg/bootstrap"
)

{{.HeaderComment}}
// newApp 手写装配:data → service → server 三段自上而下,生成器仅做锚点注入,勿整体重排。
// 退出时 cleanup 按 LIFO 逆序执行。
func newApp(ctx context.Context, bs *bootstrap.Bootstrap) ([]transport.Server, func(), error) {
	var cleanups []func()
	rollback := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

{{.InfraBlock}}

{{.RepoBlock}}

{{.ServiceBlock}}

{{.TransportBlock}}

	return []transport.Server{
{{.NewAppArgs}}	}, rollback, nil
}
