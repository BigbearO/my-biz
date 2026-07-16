package com.bigbear.quarkus.grpc.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.context.UserContext;
import com.bigbear.quarkus.core.model.LoginUser;
import com.bigbear.quarkus.core.util.HeaderUtils;
import com.bigbear.quarkus.grpc.constants.GrpcHeaders;
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
 * gRPC 客户端拦截器：从 {@link UserContext} 取当前用户（在 gRPC 服务端拦截器 / REST 过滤器中已注入），
 * 连同 Authorization / X-Client-Id 一并写入出站 Metadata，向下游零解析透传。
 *
 * <p>挂入方式：Quarkus 通过 {@code @GlobalInterceptor} 自动注册（无需额外配置）。
 */
@ApplicationScoped
@GlobalInterceptor
public class AuthClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                LoginUser user = UserContext.current();
                if (user != null && user.getUserId() != null) {
                    headers.put(GrpcHeaders.USER_ID_HEADER, user.getUserId());
                    headers.put(GrpcHeaders.USER_INFO_HEADER, HeaderUtils.toJson(user));
                }

                // 透传入站时暂存的 Authorization 与 X-Client-Id
                String auth = MetadataInterceptor.AUTH.get();
                String clientId = MetadataInterceptor.CLIENT.get();
                if (auth != null) {
                    headers.put(GrpcHeaders.AUTH_TOKEN_HEADER, auth);
                }
                if (clientId != null) {
                    headers.put(GrpcHeaders.CLIENT_ID_HEADER, clientId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
