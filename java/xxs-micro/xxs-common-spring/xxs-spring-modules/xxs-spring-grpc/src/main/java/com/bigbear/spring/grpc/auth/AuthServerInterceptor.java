package com.bigbear.spring.grpc.auth;

import com.bigbear.spring.auth.LoginUser;
import com.bigbear.spring.auth.UserContext;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;


/**
 * gRPC 服务端拦截器：Metadata → Context。
 *
 * <p>将入站 Metadata 四头还原为 {@link LoginUser}：
 * <ul>
 *   <li>存入 gRPC {@link Context.Key}，供本进程内 gRPC 调用链读取；</li>
 *   <li>同时写入 {@link UserContext}（ThreadLocal），供同步（Servlet）业务读取。</li>
 * </ul>
 * {@code userId} 为空即匿名放行。
 *
 * <p>挂入方式：引入 devh {@code grpc-server-spring-boot-starter} 后，
 * 经 {@code @GrpcGlobalServerInterceptor} 自动注册；否则在 gRPC ServerBuilder 中手动 add。
 */
@GrpcGlobalServerInterceptor
public class AuthServerInterceptor implements ServerInterceptor {

    public static final Context.Key<LoginUser> USER_KEY = Context.key("loginUser");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String userId = headers.get(GrpcHeaders.USER_ID);
        String infoJson = headers.get(GrpcHeaders.USER_INFO);

        LoginUser user = (userId == null) ? null
            : com.bigbear.spring.auth.JsonUtils.toBean(infoJson, LoginUser.class);

        // 同步（Servlet）业务通过 ThreadLocal 读取
        UserContext.set(user);
        // gRPC 调用链通过 Context 读取
        Context ctx = Context.current().withValue(USER_KEY, user);

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
