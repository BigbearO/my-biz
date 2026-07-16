package com.bigbear.spring.grpc.auth;

import com.bigbear.spring.auth.AuthHeaders;
import com.bigbear.spring.auth.LoginUser;
import com.bigbear.spring.auth.RequestUserUtils;
import com.bigbear.spring.auth.UserContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux 入站过滤器（兼容网关 / WebFlux 服务）：从网关注入的身份头构建 {@link LoginUser}
 * 并放入 {@link UserContext}，请求结束清空。
 *
 * <p>由消费方按需注册（如 {@code @Bean} 返回 {@code WebFilter}），本模块不全局自动注册。
 */
public class AuthWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        LoginUser user = RequestUserUtils.from(req);
        UserContext.set(user);
        UserContext.setRequestAuth(req.getHeaders().getFirst(AuthHeaders.HEADER_AUTHORIZATION));
        UserContext.setRequestClient(req.getHeaders().getFirst(AuthHeaders.HEADER_CLIENT_ID));
        return chain.filter(exchange).doFinally(signal -> UserContext.clearAll());
    }
}
