package com.bigbear.quarkus.seata.runtime;

import org.apache.seata.core.context.RootContext;
import org.slf4j.LoggerFactory;

import com.bigbear.quarkus.seata.config.SeataConfigConstants;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Seata gRPC 客户端拦截器：将当前全局事务 XID（优先取 gRPC Context，其次 {@link RootContext}）写入出站
 * {@code TX_XID} 头，使下游服务（Go / Quarkus 分支）参与同一全局事务。
 *
 * <p>挂入方式：Quarkus 通过 {@code @GlobalInterceptor} 自动注册（无需额外配置）。
 *
 * @author xxs-micro
 */
@ApplicationScoped
@GlobalInterceptor
public class SeataClientInterceptor implements ClientInterceptor {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SeataClientInterceptor.class);

    /** TX_XID 头元数据键（ASCII） */
    private static final Metadata.Key<String> XID_HEADER =
            Metadata.Key.of(SeataConfigConstants.TX_XID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String xid = SeataXidContext.get();
                if (xid == null) {
                    xid = RootContext.getXID();
                }
                if (xid != null && !xid.isBlank()) {
                    headers.put(XID_HEADER, xid);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
