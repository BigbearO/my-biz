package com.bigbear.spring.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 入站身份读取工具（零解析）：从 HTTP 请求头取 {@link LoginUser}。
 *
 * <p>网关已在边界校验一次并注入身份头，下游仅读头、不解析 Token。
 * {@code userId} 为空即视为匿名（对应网关白名单）。
 */
public final class RequestUserUtils {

    private RequestUserUtils() {
    }

    /**
     * 从当前 Servlet 上下文自动获取请求并解析登录用户。
     *
     * @return 登录用户，匿名时返回 null
     */
    public static LoginUser from() {
        HttpServletRequest request = UserContext.getRequest();
        return request == null ? null : from(request);
    }

    /**
     * 从 Map 形式的请求头中解析登录用户。
     *
     * @param headers 请求头 Map
     * @return 登录用户，匿名时返回 null
     */
    public static LoginUser from(Map<String, String> headers) {
        String userId = headers.get(AuthHeaders.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LoginUser info = JsonUtils.toBean(headers.get(AuthHeaders.HEADER_USER_INFO), LoginUser.class);
        return merge(userId, info);
    }

    /**
     * 从 Servlet 请求中解析登录用户。
     *
     * @param request HttpServletRequest
     * @return 登录用户，匿名时返回 null
     */
    public static LoginUser from(HttpServletRequest request) {
        String userId = request.getHeader(AuthHeaders.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LoginUser info = JsonUtils.toBean(request.getHeader(AuthHeaders.HEADER_USER_INFO), LoginUser.class);
        return merge(userId, info);
    }

    /**
     * 从 WebFlux 请求中解析登录用户。
     *
     * @param request ServerHttpRequest
     * @return 登录用户，匿名时返回 null
     */
    public static LoginUser from(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst(AuthHeaders.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LoginUser info = JsonUtils.toBean(
            request.getHeaders().getFirst(AuthHeaders.HEADER_USER_INFO), LoginUser.class);
        return merge(userId, info);
    }

    private static LoginUser merge(String userId, LoginUser info) {
        if (info == null) {
            return new LoginUser(userId, List.of(), null, null, null);
        }
        if (info.userId() == null || info.userId().isBlank()) {
            return new LoginUser(userId, info.roles(), info.username(), info.tenantId(), info.deviceId());
        }
        return info;
    }
}