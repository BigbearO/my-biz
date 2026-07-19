package com.bigbear.spring.grpc.nacos.resolver;

import com.alibaba.nacos.api.naming.NamingService;
import com.bigbear.spring.grpc.nacos.properties.NacosNamingProperties;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import java.net.URI;

/**
 * 注册 {@code scheme=nacos} 的 {@link NameResolverProvider}。
 *
 * <p>因 SPI 机制无法向 Provider 注入 Spring Bean（NamingService / 配置），故在
 * {@code GrpcNacosClientAutoConfiguration} 中以编程式注册到 gRPC 全局 Registry。
 */
public class NacosNameResolverProvider extends NameResolverProvider {

    /** Nacos 命名服务。 */
    private final NamingService namingService;

    /** Nacos 命名配置。 */
    private final NacosNamingProperties properties;

    /**
     * 构造 Nacos NameResolver Provider。
     *
     * @param namingService Nacos 命名服务
     * @param properties    Nacos 命名配置
     */
    public NacosNameResolverProvider(NamingService namingService, NacosNamingProperties properties) {
        this.namingService = namingService;
        this.properties = properties;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        return new NacosNameResolver(targetUri, namingService, properties);
    }

    @Override
    public String getDefaultScheme() {
        return "nacos";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int priority() {
        return 5;
    }
}
