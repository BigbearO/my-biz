package com.bigbear.spring.grpc.auth;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import com.bigbear.spring.auth.AuthHeaders;
import io.grpc.Metadata;


/**
 * gRPC Metadata key 定义（与 HTTP 头逐字一致）。
 */
public final class GrpcHeaders {

    public static final Metadata.Key<String> USER_ID =
        Metadata.Key.of(AuthHeaders.HEADER_USER_ID, ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> USER_INFO =
        Metadata.Key.of(AuthHeaders.HEADER_USER_INFO, ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> AUTHORIZATION =
        Metadata.Key.of(AuthHeaders.HEADER_AUTHORIZATION, ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> CLIENT_ID =
        Metadata.Key.of(AuthHeaders.HEADER_CLIENT_ID, ASCII_STRING_MARSHALLER);

    private GrpcHeaders() {
    }
}
