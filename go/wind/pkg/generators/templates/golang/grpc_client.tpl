package client

import (
	"context"

	"{{.Module}}/pkg/bootstrap"
	"{{.Module}}/pkg/serviceid"

	{{.ApiPackage}} "{{.Module}}/api/gen/go/{{.ApiModuleName}}/service/{{.ApiPackageVersion}}"
)

// New{{pascal .Model}}ServiceClient 创建{{pascal .Model}}服务的 gRPC 客户端(经 nacos 服务发现)。
// 目标服务名取 pkg/serviceid 常量;若目标服务未在 serviceid 登记,请先补常量。
func New{{pascal .Model}}ServiceClient(ctx context.Context, bs *bootstrap.Bootstrap) ({{.ApiPackage}}.{{pascal .Model}}ServiceClient, func(), error) {
	conn, err := bs.NewGRPCClientConn(ctx, serviceid.{{renderServiceName .Model}})
	if err != nil {
		return nil, nil, err
	}
	return {{.ApiPackage}}.New{{pascal .Model}}ServiceClient(conn), func() { _ = conn.Close() }, nil
}
