package com.bigbear.spring.auth;

/**
 * 跨语言鉴权透传四头常量（与 Quarkus / Go-kratos 逐字一致）。
 *
 * <ul>
 *   <li>{@link #HEADER_USER_ID}：用户唯一 ID（网关取自 Redis 用户态）</li>
 *   <li>{@link #HEADER_USER_INFO}：完整用户信息 JSON（含 userId/roles/username/tenantId/deviceId）</li>
 *   <li>{@link #HEADER_AUTHORIZATION}：原始 Bearer token（透传给需调下游三方/Keycloak 的服务）</li>
 *   <li>{@link #HEADER_CLIENT_ID}：调用方客户端标识（服务间调用时由发起方写入）</li>
 * </ul>
 */
public interface AuthHeaders {

    String HEADER_USER_ID = "X-User-Id";

    String HEADER_USER_INFO = "X-User-Info";

    String HEADER_AUTHORIZATION = "Authorization";

    String HEADER_CLIENT_ID = "X-Client-Id";
}
