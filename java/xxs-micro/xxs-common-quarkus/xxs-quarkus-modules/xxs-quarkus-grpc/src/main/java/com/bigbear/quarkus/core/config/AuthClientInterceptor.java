package com.bigbear.quarkus.core.config;

import com.bigbear.quarkus.core.constants.GrpcHeaders;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import jakarta.inject.Singleton;

@Singleton
public class AuthClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 添加认证和用户信息
                headers.put(GrpcHeaders.AUTH_TOKEN_HEADER,  "1" );
                headers.put(GrpcHeaders.USER_ID_HEADER,"1"  );

                super.start(responseListener, headers);
            }
        };
    }

}
