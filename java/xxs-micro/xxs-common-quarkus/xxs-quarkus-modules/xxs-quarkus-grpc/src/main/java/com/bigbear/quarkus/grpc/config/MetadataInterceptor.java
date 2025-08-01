package com.bigbear.quarkus.grpc.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.grpc.constants.GrpcHeaders;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@GlobalInterceptor
public class MetadataInterceptor implements ServerInterceptor {

    public static Context.Key<String>  USER = Context.key(CommonConstants.USER_ID);
    public static Context.Key<String>  CLIENT = Context.key(CommonConstants.CLIENT_ID);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        // 获取 metadata
        String userAgent = headers.get(GrpcHeaders.USER_ID_HEADER);
        System.out.println("userAgent:"+userAgent);


        Context ctx = Context.current().withValue(CLIENT, "order")
            .withValue(USER,userAgent);
//
        return Contexts.interceptCall(ctx, call, headers, next);

//        return next.startCall(call, headers);
    }
}
