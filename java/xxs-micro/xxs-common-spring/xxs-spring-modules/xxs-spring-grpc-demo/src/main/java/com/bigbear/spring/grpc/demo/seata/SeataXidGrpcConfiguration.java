package com.bigbear.spring.grpc.demo.seata;

import io.grpc.ClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;

/**
 * 全局事务 XID 透传自动装配。
 *
 * <p>经官方 {@code @GlobalClientInterceptor} 将 {@link SeataXidClientInterceptor}
 * 注册到 demo 的所有 gRPC 通道，出站时自动携带当前全局事务 XID。
 */
@Configuration(proxyBeanMethods = false)
public class SeataXidGrpcConfiguration {

    /**
     * 注册 gRPC 客户端全局拦截器。
     *
     * @return 全局事务 XID 透传拦截器实例
     */
    @Bean
    @Order(200)
    @GlobalClientInterceptor
    public ClientInterceptor seataXidClientInterceptor() {
        return new SeataXidClientInterceptor();
    }
}
