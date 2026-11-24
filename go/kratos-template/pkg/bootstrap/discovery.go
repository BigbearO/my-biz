package bootstrap

import (
	"context"

	"google.golang.org/grpc"

	grpcx "github.com/go-kratos/kratos/v3/transport/grpc"
)

// NewGRPCClientConn 建立带 nacos 服务发现的 gRPC 连接。
// target 为被调服务的注册名(约定 project/name,与 nacos 控制台一致),
// 例:conn, err := bs.NewGRPCClientConn(ctx, "my-biz/admin-service")。
// 未启用注册中心时退化为直连(target 需为 host:port)。
func (bs *Bootstrap) NewGRPCClientConn(ctx context.Context, target string, opts ...grpcx.ClientOption) (*grpc.ClientConn, error) {
	if bs.Discovery == nil {
		return grpcx.NewClient(ctx, append([]grpcx.ClientOption{grpcx.WithEndpoint(target)}, opts...)...)
	}
	base := []grpcx.ClientOption{
		grpcx.WithEndpoint("discovery:///" + target),
		grpcx.WithDiscovery(bs.Discovery),
	}
	return grpcx.NewClient(ctx, append(base, opts...)...)
}
