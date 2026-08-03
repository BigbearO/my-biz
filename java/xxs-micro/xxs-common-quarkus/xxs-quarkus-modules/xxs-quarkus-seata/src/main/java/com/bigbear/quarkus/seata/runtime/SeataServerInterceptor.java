package com.bigbear.quarkus.seata.runtime;

import org.apache.seata.common.util.StringUtils;
import org.slf4j.LoggerFactory;

import com.bigbear.quarkus.seata.config.SeataConfigConstants;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Seata gRPC 服务端拦截器：从入站 Metadata 读取全局事务 XID（{@code TX_XID} 头，与 Spring 发起方对齐），
 * 写入 gRPC Context。
 *
 * <p><b>设计要点（reactive 安全）：</b>Quarkus gRPC 服务方法返回 {@code Uni}，业务逻辑在 gRPC Context 携带的
 * worker 线程上执行，与拦截器所在的 event-loop 线程不同。因此拦截器<b>不</b>直接绑定
 * {@link org.apache.seata.core.context.RootContext}（ThreadLocal，跨线程无效且有并发竞态），
 * 仅将 XID 放入 gRPC Context（随调用传播到执行线程）；业务侧在执行 DB 写前调用
 * {@link SeataXidContext#runWithXid} 在当前执行线程上重绑 RootContext。
 *
 * <p>挂入方式：Quarkus 通过 {@code @GlobalInterceptor} 自动注册（无需额外配置）。
 *
 * @author xxs-micro
 */
@ApplicationScoped
@GlobalInterceptor
public class SeataServerInterceptor implements ServerInterceptor {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SeataServerInterceptor.class);

    /** TX_XID 头元数据键（ASCII） */
    private static final Metadata.Key<String> XID_HEADER =
            Metadata.Key.of(SeataConfigConstants.TX_XID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String xid = headers.get(XID_HEADER);
        if (StringUtils.isBlank(xid)) {
            return next.startCall(call, headers);
        }

        LOGGER.debug("seata xid [{}] propagated to grpc context", xid);
        Context ctx = Context.current().withValue(SeataXidContext.XID, xid);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
