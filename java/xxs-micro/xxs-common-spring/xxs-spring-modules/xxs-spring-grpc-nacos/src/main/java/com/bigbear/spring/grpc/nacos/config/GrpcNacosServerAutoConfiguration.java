package com.bigbear.spring.grpc.nacos.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.bigbear.spring.grpc.nacos.properties.NacosNamingProperties;
import com.bigbear.spring.grpc.nacos.registrar.NacosGrpcRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos gRPC 服务端自动装配。
 *
 * <p>当 {@code grpc.nacos.enabled=true} 且配置了 {@code spring.grpc.server.port} 时，
 * 通过 {@link NacosGrpcRegistrar} 将 gRPC 端口以 {@code <appName>-grpc} 服务名注册到 Nacos。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "grpc.nacos", name = "enabled", havingValue = "true")
@ConditionalOnProperty(name = "spring.grpc.server.port")
public class GrpcNacosServerAutoConfiguration {

    /**
     * 创建 gRPC 端口注册器（应用启动后向 Nacos 注册，关闭前注销）。
     *
     * @param properties    Nacos 命名配置
     * @param namingService Nacos 命名服务
     * @return gRPC 端口注册器
     */
    @Bean
    public NacosGrpcRegistrar nacosGrpcRegistrar(NacosNamingProperties properties, NamingService namingService,
        @Value("${spring.application.name:}") String applicationName,
        @Value("${spring.grpc.server.port:0}") int grpcPort) {
        return new NacosGrpcRegistrar(properties, namingService, applicationName, grpcPort);
    }
}
