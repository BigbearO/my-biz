package com.bigbear.quarkus.restrpc.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.quarkus.restrpc.util.HeaderUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

//请求头传递

@Provider
@ApplicationScoped
public class IncomingRequestFilter implements ContainerResponseFilter {


    @Override
    public void filter(ContainerRequestContext containerRequestContext,
        ContainerResponseContext containerResponseContext) throws IOException {
        String customHeader = containerRequestContext.getHeaderString(CommonConstants.USER_ID);
        if (customHeader != null) {
            containerRequestContext.getHeaders().add(CommonConstants.USER_ID, customHeader);
        }
    }
}
