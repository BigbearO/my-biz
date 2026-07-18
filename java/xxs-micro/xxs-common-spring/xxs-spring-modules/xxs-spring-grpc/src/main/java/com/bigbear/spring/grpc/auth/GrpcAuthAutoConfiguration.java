package com.bigbear.spring.grpc.auth;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;

/**
 * gRPC 鉴权透传自动装配（基于 Spring Boot 4.1 官方原生 spring-grpc）。
 *
 * <p>零配置开箱即用：
 * <ul>
 *   <li>{@link AuthServerInterceptor} 经官方 {@code @GlobalServerInterceptor} 注册到所有 gRPC 服务，
 *       将入站 Metadata 四头还原为 {@link com.bigbear.spring.auth.LoginUser}；</li>
 *   <li>{@link AuthClientInterceptor} 经官方 {@code @GlobalClientInterceptor} 注册到所有 gRPC 通道，
 *       出站时把当前用户透传给下游。</li>
 * </ul>
 *
 * <p>服务端 / 客户端能力分别按 {@code @ConditionalOnClass} 隔离，消费方即使只引入
 * server 或只引入 client，对应拦截器也能正常装配，互不依赖。
 */
@Configuration(proxyBeanMethods = false)
public class GrpcAuthAutoConfiguration {

    /** 服务端全局拦截器：入站身份还原。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GlobalServerInterceptor.class)
    static class ServerAuthConfiguration {

        /**
         * 注册 gRPC 服务端全局拦截器。
         *
         * <p>经 {@code @GlobalServerInterceptor} 自动作用于所有 gRPC 服务，
         * 将 Metadata 四头还原为 {@link com.bigbear.spring.auth.LoginUser} 并写入 {@link com.bigbear.spring.auth.UserContext}。
         *
         * @return gRPC 服务端拦截器实例
         */
        @Bean
        @Order(100)
        @GlobalServerInterceptor
        public ServerInterceptor authServerInterceptor() {
            return new AuthServerInterceptor();
        }
    }

    /** 客户端全局拦截器：出站身份透传。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GlobalClientInterceptor.class)
    static class ClientAuthConfiguration {

        /**
         * 注册 gRPC 客户端全局拦截器。
         *
         * <p>经 {@code @GlobalClientInterceptor} 自动作用于所有 gRPC 通道，
         * 出站时将 {@link com.bigbear.spring.auth.UserContext} 中的当前用户与
         * 入站 {@code Authorization}/{@code X-Client-Id} 透传给下游。
         *
         * @return gRPC 客户端拦截器实例
         */
        @Bean
        @Order(100)
        @GlobalClientInterceptor
        public ClientInterceptor authClientInterceptor() {
            return new AuthClientInterceptor();
        }
    }
}
