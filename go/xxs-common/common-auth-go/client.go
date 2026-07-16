package auth

import (
	"context"
	"encoding/json"

	"github.com/go-kratos/kratos/v2/metadata"
	"google.golang.org/grpc"
)

// ClientInterceptor 返回 grpc.DialOption，挂入 grpc.NewClient(...)。
//
// 从 ctx 取当前用户（在 ServerMiddleware 中已注入），连同入站
// Authorization / X-Client-Id 一并向下游零解析透传；下游 kratos-authn
// 可直接从 Metadata 读取 Bearer。
//
// 用法：conn, _ := grpc.NewClient(target, auth.ClientInterceptor())
func ClientInterceptor() grpc.DialOption {
	return grpc.WithUnaryInterceptor(
		func(
			ctx context.Context,
			method string,
			req, reply interface{},
			cc *grpc.ClientConn,
			invoker grpc.UnaryInvoker,
			opts ...grpc.CallOption,
		) error {
			if u, ok := ctx.Value(userKey).(*LoginUser); ok && u != nil {
				ctx = metadata.AppendToClientContext(ctx,
					HeaderUserID, u.UserID,
					HeaderUserInfo, toJSON(u),
				)
			}
			if auth, ok := ctx.Value(authKey).(string); ok && auth != "" {
				ctx = metadata.AppendToClientContext(ctx, HeaderAuth, auth)
			}
			if clientID, ok := ctx.Value(clientKey).(string); ok && clientID != "" {
				ctx = metadata.AppendToClientContext(ctx, HeaderClientID, clientID)
			}
			return invoker(ctx, method, req, reply, cc, opts...)
		},
	)
}

func toJSON(u *LoginUser) string {
	b, err := json.Marshal(u)
	if err != nil {
		return "{}"
	}
	return string(b)
}
