package com.bigbear.quarkus.grpc.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.context.UserContext;
import com.bigbear.quarkus.core.model.LoginUser;
import com.bigbear.quarkus.core.util.HeaderUtils;
import com.bigbear.quarkus.grpc.constants.GrpcHeaders;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * gRPC 服务端拦截器：将入站 Metadata 中的四头还原为 {@link LoginUser} 并放入 {@link UserContext}，
 * 供业务方法透明获取；同时把 Authorization / X-Client-Id 暂存到 gRPC Context，
 * 供 {@link AuthClientInterceptor} 向下游透传。
 *
 * <p>挂入方式：Quarkus 通过 {@code @GlobalInterceptor} 自动注册（无需额外配置）。
 */
@ApplicationScoped
@GlobalInterceptor
public class MetadataInterceptor implements ServerInterceptor {

    public static Context.Key<String> CLIENT = Context.key(CommonConstants.CLIENT_ID);
    public static Context.Key<String> AUTH = Context.key(CommonConstants.AUTH);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String userId = headers.get(GrpcHeaders.USER_ID_HEADER);
        String infoJson = headers.get(GrpcHeaders.USER_INFO_HEADER);
        String auth = headers.get(GrpcHeaders.AUTH_TOKEN_HEADER);
        String clientId = headers.get(GrpcHeaders.CLIENT_ID_HEADER);

        LoginUser user = null;
        if (userId != null && !userId.isBlank()) {
            user = HeaderUtils.parseLoginUser(infoJson);
            if (user == null) {
                user = new LoginUser();
            }
            if (user.getUserId() == null) {
                user.setUserId(userId);
            }
        }
        UserContext.set(user);

        Context ctx = Context.current()
            .withValue(CLIENT, clientId)
            .withValue(AUTH, auth);

        ServerCall.Listener<ReqT> listener = Contexts.interceptCall(ctx, call, headers, next);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onComplete() {
                UserContext.clear();
                super.onComplete();
            }

            @Override
            public void onCancel() {
                UserContext.clear();
                super.onCancel();
            }
        };
    }
}
