package com.bigbear.quarkus.core.context;

import com.bigbear.quarkus.core.model.LoginUser;
import com.bigbear.quarkus.core.util.HeaderUtils;
import com.bigbear.quarkus.core.util.QuarkusUtil;

/**
 * 统一身份上下文：业务侧透明获取当前登录用户。
 *
 * <p>取值优先级：
 * <ol>
 *   <li>gRPC 服务端拦截器 {@code MetadataInterceptor} 注入的 ThreadLocal（gRPC 入站）；</li>
 *   <li>REST 入站：通过请求头即时读取（{@code HeaderUtils}，网关已注入身份头）；</li>
 * </ol>
 * 全程零解析、只读头；{@code userId} 为空即匿名（对应网关白名单）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /** gRPC 入站拦截器调用：注入当前用户（匿名传 null）。 */
    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    /** 清空（请求结束时调用，避免 ThreadLocal 泄漏）。 */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 取当前登录用户；无则返回 null（匿名）。
     */
    public static LoginUser current() {
        LoginUser user = CURRENT.get();
        if (user != null) {
            return user;
        }
        // REST 入站回退：从请求头即时读取
        HeaderUtils bean = QuarkusUtil.getBean(HeaderUtils.class);
        if (bean != null) {
            return bean.getLoginUser();
        }
        return null;
    }

    public static boolean isAnonymous() {
        LoginUser user = current();
        return user == null || user.isAnonymous();
    }

    /** 手动注入（测试 / 非 HTTP 上下文）。返回原值便于还原。 */
    public static LoginUser withUser(LoginUser user) {
        LoginUser prev = CURRENT.get();
        CURRENT.set(user);
        return prev;
    }
}
