package com.bigbear.quarkus.core.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@ApplicationScoped
public class IncomingRequestFilter implements ContainerRequestFilter {

    // 存储当前请求的 Authorization 头
    private static final ThreadLocal<String> authHeader = new ThreadLocal<>();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 从当前 HTTP 请求中获取 Authorization 头
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        authHeader.set(authorization);
    }

    public static String getCurrentAuthHeader() {
        return authHeader.get();
    }

    public static void clear() {
        authHeader.remove();
    }
}
