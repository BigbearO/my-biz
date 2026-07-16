package com.bigbear.spring.grpc.auth;


import net.devh.boot.grpc.server.interceptor.GlobalServerInterceptorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC 鉴权透传自动装配。
 *
 * <p>仅当消费方引入 devh {@code grpc-server-spring-boot-starter}（提供
 * {@code GlobalServerInterceptorRegistry}）时生效，将 {@link AuthServerInterceptor}
 * 经 {@code @GrpcGlobalServerInterceptor} 自动注册到 gRPC 服务端；
 * {@link AuthClientInterceptor} 注册为 bean 供 {@code @GrpcClient} 调用链使用。
 *
 * <p>未引入 devh 时本配置整体跳过，拦截器类仍可直接手动挂载到 gRPC ServerBuilder。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(GlobalServerInterceptorRegistry.class)
public class GrpcAuthAutoConfiguration {

    @Bean
    public AuthServerInterceptor authServerInterceptor() {
        return new AuthServerInterceptor();
    }

    @Bean
    public AuthClientInterceptor authClientInterceptor() {
        return new AuthClientInterceptor();
    }
}
