package com.bigbear.spring.grpc.auth;

import com.bigbear.spring.auth.AuthHeaders;
import com.bigbear.spring.auth.LoginUser;
import com.bigbear.spring.auth.RequestUserUtils;
import com.bigbear.spring.auth.UserContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Servlet 入站过滤器：从网关注入的身份头构建 {@link LoginUser} 并放入 {@link UserContext}，
 * 供本请求内（含对其余 gRPC 服务的调用）透明获取与透传。请求结束清空。
 *
 * <p>由消费方按需注册（如 {@code @Bean FilterRegistrationBean}），本模块不全局自动注册。
 */
public class AuthServletFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest hsr) {
            LoginUser user = RequestUserUtils.from(hsr);
            UserContext.set(user);
            UserContext.setRequestAuth(hsr.getHeader(AuthHeaders.HEADER_AUTHORIZATION));
            UserContext.setRequestClient(hsr.getHeader(AuthHeaders.HEADER_CLIENT_ID));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clearAll();
        }
    }
}
