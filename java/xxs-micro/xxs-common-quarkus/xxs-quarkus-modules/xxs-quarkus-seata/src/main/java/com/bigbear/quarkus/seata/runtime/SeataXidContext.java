package com.bigbear.quarkus.seata.runtime;

import java.util.function.Supplier;

import org.apache.seata.core.context.RootContext;

import com.bigbear.quarkus.seata.config.SeataConfigConstants;

import io.grpc.Context;

/**
 * Seata 全局事务 XID 的跨线程传播工具。
 *
 * <p>gRPC 服务端拦截器将入站 {@code TX_XID} 头写入 {@link io.grpc.Context}（Quarkus gRPC 会在 {@code @Blocking}
 * 方法执行线程上自动携带），业务方在执行可能切换线程的阻塞逻辑前，用 {@link #runWithXid} 将 XID 重新绑定到
 * {@link RootContext}，确保 {@code ConnectionProxyXA} 能识别到全局事务并登记 XA 分支。
 *
 * @author xxs-micro
 */
public final class SeataXidContext {

    /** gRPC Context 中承载全局事务 XID 的键 */
    public static final Context.Key<String> XID = Context.key(SeataConfigConstants.TX_XID);

    private SeataXidContext() {
    }

    /**
     * 从当前 gRPC Context 读取全局事务 XID。
     *
     * @return XID，无全局事务时为 {@code null}
     */
    public static String get() {
        return XID.get();
    }

    /**
     * 以指定 XID 绑定 {@link RootContext} 执行动作，结束后安全解绑，保证线程本地状态不泄漏。
     *
     * @param xid    全局事务 XID，为 null 时直接执行动作
     * @param action 需在全局事务上下文内执行的动作
     * @param <T>    返回值类型
     * @return 动作结果
     */
    public static <T> T runWithXid(String xid, Supplier<T> action) {
        if (xid == null || xid.isBlank()) {
            return action.get();
        }
        RootContext.bind(xid);
        try {
            return action.get();
        } finally {
            RootContext.unbind();
        }
    }
}
