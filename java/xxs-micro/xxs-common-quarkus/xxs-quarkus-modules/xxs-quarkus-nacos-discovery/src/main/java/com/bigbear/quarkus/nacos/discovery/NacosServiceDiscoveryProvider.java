package com.bigbear.quarkus.nacos.discovery;

import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.api.config.ServiceDiscoveryAttribute;
import io.smallrye.stork.api.config.ServiceDiscoveryType;
import io.smallrye.stork.spi.ServiceDiscoveryProvider;
import io.smallrye.stork.spi.StorkInfrastructure;

/**
 * Nacos 服务发现 Provider。
 * <p>
 * 通过 {@link ServiceDiscoveryType} 声明类型名为 {@code nacos}，配合 {@code META-INF/services} 的 SPI 注册，
 * 使 Quarkus Stork 在配置 {@code quarkus.stork.<svc>.service-discovery.type=nacos} 时自动发现本实现。
 *
 * @author xxs-micro
 */
@ServiceDiscoveryAttribute(name = "server-addr",
        description = "Nacos server address, e.g. 127.0.0.1:8848. Falls back to quarkus.config.source.nacos.server-addr.",
        defaultValue = "127.0.0.1:8848")
@ServiceDiscoveryAttribute(name = "namespace", description = "Nacos namespace.", defaultValue = "public")
@ServiceDiscoveryAttribute(name = "group", description = "Nacos group.", defaultValue = "DEFAULT_GROUP")
@ServiceDiscoveryAttribute(name = "refresh-period", description = "Service discovery cache refresh period.",
        defaultValue = "5M")
@ServiceDiscoveryAttribute(name = "secure",
        description = "Whether the connection with the service should be encrypted with TLS.")
@ServiceDiscoveryAttribute(name = "application",
        description = "The application name registered in Nacos; if not defined Stork service name will be used.")
@ServiceDiscoveryAttribute(name = "username", description = "Nacos auth username.", defaultValue = "")
@ServiceDiscoveryAttribute(name = "password", description = "Nacos auth password.", defaultValue = "")
@ServiceDiscoveryType("nacos")
public class NacosServiceDiscoveryProvider implements ServiceDiscoveryProvider<NacosConfiguration> {

    @Override
    public ServiceDiscovery createServiceDiscovery(NacosConfiguration config, String serviceName,
            ServiceConfig serviceConfig, StorkInfrastructure storkInfrastructure) {
        return new NacosServiceDiscovery(serviceName, config);
    }
}
