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

// GrpcServerMiddleware 从 kratos gRPC Metadata 读取鉴权头注入 context。
func GrpcServerMiddleware() middleware.Middleware {
	return func(handler middleware.Handler) middleware.Handler {
		return func(ctx context.Context, req interface{}) (interface{}, error) {
			var userID, userInfo, auth, clientID string

			if md, ok := metadata.FromServerContext(ctx); ok {
				userID = md.Get(HeaderUserID)
				userInfo = md.Get(HeaderUserInfo)
				auth = md.Get(HeaderAuth)
				clientID = md.Get(HeaderClientID)
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

// HTTPServerMiddleware 从 HTTP RequestHeader 读取鉴权头注入 context。
func HTTPServerMiddleware() middleware.Middleware {
	return func(handler middleware.Handler) middleware.Handler {
		return func(ctx context.Context, req interface{}) (interface{}, error) {
			var userID, userInfo, auth, clientID string

			if tr, ok := transport.FromServerContext(ctx); ok {
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
