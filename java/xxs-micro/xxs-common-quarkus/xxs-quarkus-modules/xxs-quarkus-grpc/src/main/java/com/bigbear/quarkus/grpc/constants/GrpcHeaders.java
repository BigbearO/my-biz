package com.bigbear.quarkus.grpc.constants;

import io.grpc.Metadata;

public interface GrpcHeaders {

    Metadata.Key<String> USER_ID_HEADER =
        Metadata.Key.of(CommonConstants.USER_ID, Metadata.ASCII_STRING_MARSHALLER);

    Metadata.Key<String> AUTH_TOKEN_HEADER =
        Metadata.Key.of(CommonConstants.AUTH, Metadata.ASCII_STRING_MARSHALLER);

    Metadata.Key<String> CLIENT_ID_HEADER =
        Metadata.Key.of(CommonConstants.CLIENT_ID, Metadata.ASCII_STRING_MARSHALLER);
}
