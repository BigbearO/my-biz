package com.bigbear.spring.grpc.auth;

import com.bigbear.spring.auth.LoginUser;
import com.bigbear.spring.auth.UserContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;


/**
 * gRPC 客户端拦截器：Context → Metadata 透传。
 *
 * <p>从 {@link AuthServerInterceptor#USER_KEY}（gRPC 调用链）或 {@link UserContext}（ThreadLocal，
 * 同步 Servlet 入站）取当前用户，连同入站 {@code Authorization} / {@code X-Client-Id}
 * 一并向下游零解析透传。
 */
public class AuthClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                LoginUser user = resolveUser();
                if (user != null && user.userId() != null) {
                    headers.put(GrpcHeaders.USER_ID, user.userId());
                    headers.put(GrpcHeaders.USER_INFO,
                        com.bigbear.spring.auth.JsonUtils.toJson(user));
                }

                String auth = UserContext.getRequestAuth();
                if (auth != null) {
                    headers.put(GrpcHeaders.AUTHORIZATION, auth);
                }
                String clientId = UserContext.getRequestClient();
                if (clientId != null) {
                    headers.put(GrpcHeaders.CLIENT_ID, clientId);
                }
                super.start(responseListener, headers);
            }
        };
    }

    private LoginUser resolveUser() {
        LoginUser fromContext = AuthServerInterceptor.USER_KEY.get();
        if (fromContext != null) {
            return fromContext;
        }
        return UserContext.current();
    }
}
