package com.bigbear.spring.grpc.demo;

import com.bigbear.spring.grpc.auth.AuthServletFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 入站过滤器注册：把网关注入的身份头还原为 {@link com.bigbear.spring.auth.UserContext}，
 * 供本请求内（含对其余 gRPC 服务的调用）透明获取与透传。
 */
@Configuration
public class AuthFilterConfig {

    /**
     * 注册 Servlet 入站鉴权过滤器，作用于所有请求路径。
     *
     * @return AuthServletFilter 的注册 Bean
     */
    @Bean
    public FilterRegistrationBean<AuthServletFilter> authServletFilter() {
        FilterRegistrationBean<AuthServletFilter> registration =
            new FilterRegistrationBean<>(new AuthServletFilter());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
