package auth

import (
	"context"
	"encoding/json"

	"github.com/go-kratos/kratos/v2/metadata"
	"github.com/go-kratos/kratos/v2/middleware"
	"github.com/go-kratos/kratos/v2/transport"
	"github.com/go-kratos/kratos/v2/transport/http"
)

type ctxKey int

const (
	userKey ctxKey = iota
	authKey
	clientKey
)

// ServerMiddleware 服务端中间件：Metadata / HTTP 头 → context。
//
// 优先从 kratos gRPC Metadata 读取；若为 REST 入站则从
// transport/http 的 RequestHeader() 读取，二者等价处理。
// userID 为空视为匿名，不注入 LoginUser（对应网关白名单）。
//
// 挂入方式：append 到 rpc.CreateGrpcServer(cfg, middlewares...) 的 middleware 链表，
// 例如：middlewares = append(middlewares, auth.ServerMiddleware())。
func ServerMiddleware() middleware.Middleware {
	return func(handler middleware.Handler) middleware.Handler {
		return func(ctx context.Context, req interface{}) (interface{}, error) {
			var userID, userInfo, auth, clientID string

			if md, ok := metadata.FromServerContext(ctx); ok {
				userID = md.Get(HeaderUserID)
				userInfo = md.Get(HeaderUserInfo)
				auth = md.Get(HeaderAuth)
				clientID = md.Get(HeaderClientID)
			} else if tr, ok := transport.FromServerContext(ctx); ok {
				if htr, ok2 := tr.(*http.Transport); ok2 {
					hdr := htr.RequestHeader()
					userID = hdr.Get(HeaderUserID)
					userInfo = hdr.Get(HeaderUserInfo)
					auth = hdr.Get(HeaderAuth)
					clientID = hdr.Get(HeaderClientID)
				}
			}

			var u *LoginUser
			if userID != "" {
				u = &LoginUser{UserID: userID}
				if userInfo != "" {
					_ = json.Unmarshal([]byte(userInfo), u)
				}
			}

			ctx = context.WithValue(ctx, userKey, u)
			ctx = context.WithValue(ctx, authKey, auth)
			ctx = context.WithValue(ctx, clientKey, clientID)
			return handler(ctx, req)
		}
	}
}

// CurrentUser 业务透明获取当前登录用户；无则返回 (nil, false)（匿名）。
func CurrentUser(ctx context.Context) (*LoginUser, bool) {
	u, ok := ctx.Value(userKey).(*LoginUser)
	return u, ok && u != nil
}

// WithUser 手动注入（测试 / 非 HTTP 上下文）。
func WithUser(ctx context.Context, u *LoginUser) context.Context {
	return context.WithValue(ctx, userKey, u)
}
