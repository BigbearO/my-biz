package com.bigbear.quarkus.core.filter;

import com.bigbear.quarkus.core.context.UserContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * REST 出站过滤器：请求结束时清空 {@link UserContext}，避免 ThreadLocal 跨请求泄漏。
 */
@Provider
public class AuthContainerResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        UserContext.clear();
    }
}
