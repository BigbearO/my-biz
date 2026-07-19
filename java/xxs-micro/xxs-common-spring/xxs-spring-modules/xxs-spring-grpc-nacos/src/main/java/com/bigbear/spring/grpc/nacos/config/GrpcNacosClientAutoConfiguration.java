package com.bigbear.spring.grpc.nacos.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.bigbear.spring.grpc.nacos.properties.NacosNamingProperties;
import com.bigbear.spring.grpc.nacos.resolver.NacosNameResolverProvider;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;

/**
 * Nacos gRPC 客户端自动装配。
 *
 * <p>职责：
 * <ol>
 *   <li>构造 Nacos {@link NamingService}；</li>
 *   <li>将 {@code nacos} scheme 的 {@link NacosNameResolverProvider} 编程式注册到 gRPC 全局 Registry
 *       （SPI 无法注入 Spring Bean，故不走自动发现）；</li>
 *   <li>为所有 gRPC Channel 设定默认 {@code round_robin} 负载均衡。</li>
 * </ol>
 *
 * <p>仅当 {@code grpc.nacos.enabled=true} 且类路径存在 Nacos 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(NamingService.class)
@ConditionalOnProperty(prefix = "grpc.nacos", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NacosNamingProperties.class)
public class GrpcNacosClientAutoConfiguration {

    /**
     * 创建 Nacos 命名服务（连接属性取自 {@link NacosNamingProperties}）。
     *
     * @param properties Nacos 命名配置
     * @return Nacos 命名服务实例
     * @throws Exception 创建命名服务失败（如地址非法）
     */
    @Bean
    public NamingService nacosNamingService(NacosNamingProperties properties) throws Exception {
        return NacosFactory.createNamingService(properties.toProperties());
    }

    /**
     * 创建并向 gRPC 全局 Registry 注册 {@code nacos} scheme 的 NameResolverProvider。
     *
     * <p>在工厂方法内注册，确保全局仅注册一次。
     *
     * @param namingService Nacos 命名服务
     * @param properties    Nacos 命名配置
     * @return 注册后的 Provider
     */
    @Bean
    public NacosNameResolverProvider nacosNameResolverProvider(NamingService namingService, NacosNamingProperties properties) {
        NacosNameResolverProvider provider = new NacosNameResolverProvider(namingService, properties);
        NameResolverRegistry.getDefaultRegistry().register(provider);
        return provider;
    }

    /**
     * 为所有 gRPC Channel 设定默认 {@code round_robin} 负载均衡策略。
     *
     * <p>Nacos 多实例场景下，默认 {@code pick_first} 只会命中首个地址；round_robin 实现均匀分发。
     *
     * <p><strong>关键依赖：</strong>本定制器依赖 {@link NacosNameResolverProvider}，可强制其在 Channel
     * 构建前完成向 gRPC 全局 Registry 的注册。spring-grpc 在构建 {@code ManagedChannel} 前必须先注入
     * {@code List<GrpcChannelBuilderCustomizer>}，由此确保 {@code nacos://} 的 NameResolverProvider
     * 必然已就绪，避免 {@code Could not find a NameResolverProvider for nacos://} 的初始化顺序问题。
     *
     * @param ignored 仅为触发 {@link NacosNameResolverProvider} 提前实例化的占位依赖
     * @return gRPC Channel 定制器
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public GrpcChannelBuilderCustomizer nacosRoundRobinCustomizer(NacosNameResolverProvider ignored) {
        return (channelName, builder) -> {
            // 所有 gRPC Channel 默认 round_robin，多实例下均匀分发（Nacos 多实例场景必备）
            builder.defaultLoadBalancingPolicy("round_robin");
        };
    }
}
