package com.bigbear.spring.auth;

/**
 * 统一身份上下文（ThreadLocal 版）：业务侧透明获取当前登录用户。
 *
 * <p>入站过滤器（Servlet / WebFlux）/ gRPC 服务端拦截器在请求开始时注入，
 * 请求结束时清空。{@code userId} 为空即匿名（对应网关白名单）。
 *
 * <p>另暂存入站 {@code Authorization} 与 {@code X-Client-Id}，
 * 供 gRPC 客户端拦截器向下游透传（kratos-authn 等可继续读取 Bearer）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_AUTH = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_CLIENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    public static LoginUser current() {
        return CURRENT.get();
    }

    public static boolean isAnonymous() {
        LoginUser user = CURRENT.get();
        return user == null || user.isAnonymous();
    }

    public static void setRequestAuth(String auth) {
        REQUEST_AUTH.set(auth);
    }

    public static String getRequestAuth() {
        return REQUEST_AUTH.get();
    }

    public static void setRequestClient(String clientId) {
        REQUEST_CLIENT.set(clientId);
    }

    public static String getRequestClient() {
        return REQUEST_CLIENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void clearAll() {
        CURRENT.remove();
        REQUEST_AUTH.remove();
        REQUEST_CLIENT.remove();
    }

    /** 手动注入（测试 / 非 HTTP 上下文）。 */
    public static void withUser(LoginUser user) {
        CURRENT.set(user);
    }
}
