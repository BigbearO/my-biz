# common-auth-go — 跨语言鉴权透传（仅 go-kratos）

为 go-kratos 服务补齐跨语言四头的服务端/客户端透传。**全程零解析、只读头**；Token 仅在边界（Spring Cloud Gateway）校验一次。

与 Java（Quarkus / Spring）侧使用逐字一致的四头常量与 `LoginUser` 字段。

## 跨语言四头契约（`consts`）

| 头 | 常量 | 用途 |
|---|---|---|
| `X-User-Id` | `HeaderUserID` | 用户唯一 ID |
| `X-User-Info` | `HeaderUserInfo` | 完整用户信息 JSON |
| `Authorization` | `HeaderAuth` | 原始 Bearer token（透传） |
| `X-Client-Id` | `HeaderClientID` | 调用方客户端标识 |

## LoginUser（`login_user.go`）

字段与 Java 侧 `LoginUser` 的 JSON tag 逐字对齐：

```go
type LoginUser struct {
    UserID   string   `json:"userId"`
    Roles    []string `json:"roles"`
    Username string   `json:"username"`
    TenantID string   `json:"tenantId"` // 多租户预留，本期单租户
    DeviceID string   `json:"deviceId"`
}
```

## 服务端中间件

按传输协议选择对应的中间件：

- **gRPC** → `GrpcServerMiddleware()`：从 kratos gRPC Metadata 读取
- **HTTP/REST** → `HTTPServerMiddleware()`：从 `transport/http` 的 `RequestHeader()` 读取

`userID` 为空视为匿名，不注入 `LoginUser`。

```go
import "bigbear.com/xxs-common/common-auth-go/auth"

// gRPC 服务
middlewares = append(middlewares, auth.GrpcServerMiddleware())
srv, err := rpc.CreateGrpcServer(cfg, middlewares...)

// HTTP/REST 服务
middlewares = append(middlewares, auth.HTTPServerMiddleware())
srv, err := rpc.CreateRestServer(cfg, middlewares...)
```

业务透明获取当前用户：

```go
u, ok := auth.CurrentUser(ctx)
if !ok {
    // 匿名：对应网关白名单
}
_ = u.UserID

// 测试 / 非 HTTP 上下文手动注入
ctx = auth.WithUser(ctx, &auth.LoginUser{UserID: "u1", Roles: []string{"biz.admin"}})
```

## 客户端拦截器 `ClientInterceptor`（grpc.DialOption）

挂进 `grpc.NewClient(...)`：从 ctx 取 `ServerMiddleware` 注入的用户，连同入站 `Authorization` / `X-Client-Id` 一并回写四头（`Authorization` 一并透传，下游 kratos-authn 可直接读 Bearer）。

```go
import "google.golang.org/grpc"

conn, err := grpc.NewClient(target, auth.ClientInterceptor())
```

## 与 go-wind-cms `pkg/metadata` 的区别

- `pkg/metadata.OperatorMetadata`（`x-md-global-operator`）：go-wind-cms **内部**跨服传递操作员，属项目内约定；
- 本包四头：`X-User-Id` / `X-User-Info` / `Authorization` / `X-Client-Id`，是**跨语言（Java/Go）统一契约**，与 Quarkus / Spring 侧逐字一致。

两者互补共存，互不替代。

## 依赖

- `github.com/go-kratos/kratos/v2`
- `google.golang.org/grpc`

已通过 `go mod tidy`，`go build ./...` 与 `go vet ./...` 通过。
