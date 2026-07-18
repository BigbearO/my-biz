package com.bigbear.spring.auth;

import com.alibaba.ttl.TransmittableThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 统一身份上下文（TTL 版）：业务侧透明获取当前登录用户。
 *
 * <p>使用 {@link TransmittableThreadLocal} 替代原生 {@link ThreadLocal}，
 * 在 {@code @Async}、{@code CompletableFuture}、线程池等异步场景下自动透传上下文，
 * 无需手动捕获/恢复。
 *
 * <p>入站过滤器（Servlet / WebFlux）/ gRPC 服务端拦截器在请求开始时注入，
 * 请求结束时清空。{@code userId} 为空即匿名（对应网关白名单）。
 *
 * <p>另暂存入站 {@code Authorization} 与 {@code X-Client-Id}，
 * 供 gRPC 客户端拦截器向下游透传（kratos-authn 等可继续读取 Bearer）。
 */
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CURRENT = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> REQUEST_AUTH = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> REQUEST_CLIENT = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前登录用户。
     *
     * @param user 登录用户信息，可为 null
     */
    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    /**
     * 获取当前登录用户。
     *
     * @return 当前登录用户，匿名时返回 null
     */
    public static LoginUser current() {
        return CURRENT.get();
    }

    /**
     * 判断当前是否为匿名用户。
     *
     * @return true 表示匿名（未登录或网关白名单）
     */
    public static boolean isAnonymous() {
        LoginUser user = CURRENT.get();
        return user == null || user.isAnonymous();
    }

    /**
     * 设置入站 Authorization 头。
     *
     * @param auth Bearer token 值
     */
    public static void setRequestAuth(String auth) {
        REQUEST_AUTH.set(auth);
    }

    /**
     * 获取入站 Authorization 头。
     *
     * @return Bearer token 值，无上下文时返回 null
     */
    public static String getRequestAuth() {
        return REQUEST_AUTH.get();
    }

    /**
     * 设置入站客户端标识。
     *
     * @param clientId 客户端标识
     */
    public static void setRequestClient(String clientId) {
        REQUEST_CLIENT.set(clientId);
    }

    /**
     * 获取入站客户端标识。
     *
     * @return 客户端标识，无上下文时返回 null
     */
    public static String getRequestClient() {
        return REQUEST_CLIENT.get();
    }

    /**
     * 仅清空当前登录用户。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 清空全部上下文（用户、Authorization、ClientId）。
     */
    public static void clearAll() {
        CURRENT.remove();
        REQUEST_AUTH.remove();
        REQUEST_CLIENT.remove();
    }

    /**
     * 手动注入（测试 / 非 HTTP 上下文）。
     *
     * @param user 登录用户信息
     */
    public static void withUser(LoginUser user) {
        CURRENT.set(user);
    }

    /**
     * 从 {@link RequestContextHolder} 获取当前 Servlet 请求。
     *
     * @return 当前 HttpServletRequest，非 Web 上下文时返回 null
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
     * 获取当前请求的指定头信息。
     *
     * @param headerName 请求头名称
     * @return 请求头值，无上下文时返回 null
     */
    public static String getHeader(String headerName) {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getHeader(headerName);
    }
}