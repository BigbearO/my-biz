package server

import (
	"context"

	"github.com/go-kratos/kratos/v2/middleware"
	"google.golang.org/grpc"

	seataGrpc "seata.apache.org/seata-go/v2/pkg/integration/grpc"

	"bear-admin/app/ai/service/internal/data"
)

// SeataXidMiddleware 还原全局事务 XID 到 gRPC 入站 context。
//
// 复用 seata-go 官方 ServerTransactionInterceptor 语义：从入站 Metadata 取 TX_XID，
// 有则 tm.InitSeataContext + tm.SetXID 绑定到本次调用 context，使分支（seata-xa-postgres 驱动）
// 在 ExecContext 时自动注册 XA 分支并随全局事务提交/回滚。
//
// 当 seatago.yml 未开启 seata（seata.enabled 非 true）时返回透传 middleware，
// 避免在未初始化 seata 客户端的情况下解析 XID。
func SeataXidMiddleware() middleware.Middleware {
	if !data.SeataEnabled() {
		return func(handler middleware.Handler) middleware.Handler {
			return func(ctx context.Context, req interface{}) (interface{}, error) {
				return handler(ctx, req)
			}
		}
	}

	return func(handler middleware.Handler) middleware.Handler {
		return func(ctx context.Context, req interface{}) (interface{}, error) {
			return seataGrpc.ServerTransactionInterceptor(ctx, req, nil, grpc.UnaryHandler(handler))
		}
	}
}
