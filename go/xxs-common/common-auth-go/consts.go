// Package auth 提供跨语言鉴权透传能力（仅面向 go-kratos）。
//
// 设计：Token 仅在边界（Spring Cloud Gateway）校验一次，校验通过后注入
// X-User-Id / X-User-Info（HTTP 头）与 gRPC Metadata 透传，下游零解析、只读头。
// 本包与 Java Quarkus / Spring 侧使用逐字一致的四头常量与 LoginUser 字段。
package auth

// 跨语言鉴权透传四头常量（与 Java Quarkus / Spring 逐字一致）。
const (
	// HeaderUserID 用户唯一 ID（网关取自 Redis 用户态）。
	HeaderUserID = "X-User-Id"
	// HeaderUserInfo 完整用户信息 JSON（含 userId/roles/username/tenantId/deviceId）。
	HeaderUserInfo = "X-User-Info"
	// HeaderAuth 原始 Bearer token（透传给需调下游三方/Keycloak 的服务）。
	HeaderAuth = "Authorization"
	// HeaderClientID 调用方客户端标识（服务间调用时由发起方写入）。
	HeaderClientID = "X-Client-Id"
)
