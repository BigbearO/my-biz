package com.bigbear.spring.grpc.demo.seata;

import org.apache.seata.core.context.RootContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * gRPC 客户端拦截器：全局事务 XID 透传（Seata → Go 分支）。
 *
 * <p>当当前线程存在全局事务（{@link RootContext#getXID()} 非空）时，将 XID 写入出站
 * Metadata {@code TX_XID} 头，供 seata-go 分支在服务端还原 XID 并注册为同一全局事务的分支。
 */
public class SeataXidClientInterceptor implements ClientInterceptor {

    /** 与 seata-go 入站中间件约定的 XID 透传头。 */
    public static final Metadata.Key<String> XID_KEY =
        Metadata.Key.of("TX_XID", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String xid = RootContext.getXID();
                if (xid != null) {
                    headers.put(XID_KEY, xid);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
