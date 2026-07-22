package com.bigbear.quarkus.nacos.registrar;

import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.api.config.ServiceRegistrarAttribute;
import io.smallrye.stork.api.config.ServiceRegistrarType;
import io.smallrye.stork.spi.ServiceRegistrarProvider;
import io.smallrye.stork.spi.StorkInfrastructure;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Nacos 服务注册 Provider。
 * <p>
 * 通过 {@link ServiceRegistrarType} 声明类型名为 {@code nacos}，并作为 {@link ApplicationScoped} Bean 被 Quarkus Stork 自动发现。
 * 在 Quarkus 下，自定义 Provider 由 CDI 容器托管（而非 {@code META-INF/services} 的 ServiceLoader），
 * 因此本类必须声明 {@link ApplicationScoped}，且所属库需生成 Jandex 索引，
 * 使 Quarkus 在配置 {@code quarkus.stork.<svc>.service-registrar.type=nacos} 时能扫描到本实现。
 *
 * @author xxs-micro
 */
@ApplicationScoped
@ServiceRegistrarType(value = "nacos", metadataKey = NacosMetadataKey.class)
@ServiceRegistrarAttribute(name = "server-addr",
        description = "Nacos server address, e.g. 127.0.0.1:8848. Falls back to quarkus.config.source.nacos.server-addr.",
        defaultValue = "127.0.0.1:8848")
@ServiceRegistrarAttribute(name = "namespace", description = "Nacos namespace.", defaultValue = "public")
@ServiceRegistrarAttribute(name = "group", description = "Nacos group.", defaultValue = "DEFAULT_GROUP")
@ServiceRegistrarAttribute(name = "username", description = "Nacos auth username.", defaultValue = "")
@ServiceRegistrarAttribute(name = "password", description = "Nacos auth password.", defaultValue = "")
@ServiceRegistrarAttribute(name = "cluster-name", description = "Nacos cluster name.", defaultValue = "DEFAULT")
@ServiceRegistrarAttribute(name = "weight", description = "Instance weight.", defaultValue = "1.0")
@ServiceRegistrarAttribute(name = "ephemeral",
        description = "Whether the instance is ephemeral (relies on client heartbeat).", defaultValue = "true")
public class NacosServiceRegistrarProvider
        implements ServiceRegistrarProvider<NacosRegistrarConfiguration, NacosMetadataKey> {

    @Override
    public ServiceRegistrar<NacosMetadataKey> createServiceRegistrar(NacosRegistrarConfiguration config,
                                                                     String serviceRegistrarName, StorkInfrastructure infrastructure) {
        return new NacosServiceRegistrar(config, serviceRegistrarName, infrastructure);
    }

    /**
     * 创建携带注册 IP/端口的 Nacos 服务注册器。
     * <p>
     * Quarkus 的自动注册 gate 仅覆盖 Consul/Eureka/Static，不触发自定义 {@code nacos} 注册器，
     * 因此由 {@link NacosServiceRegistrarLoader} 在装配阶段取出 {@code host/port} 并透传至此，
     * 最终由 {@link NacosStorkRegistration} 在应用启动后调用 {@code registerNow()} 完成注册。
     *
     * @param config                Nacos 服务注册配置
     * @param serviceRegistrarName  Stork 注册名（同时作为 Nacos 服务名）
     * @param infrastructure        Stork 基础设施
     * @param host                  注册实例 IP（来自 service-registrar.parameters.host）
     * @param port                  注册实例端口（来自 service-registrar.parameters.port，字符串形式）
     * @return Nacos 服务注册器
     */
    public ServiceRegistrar<NacosMetadataKey> createServiceRegistrar(NacosRegistrarConfiguration config,
                                                                     String serviceRegistrarName, StorkInfrastructure infrastructure,
                                                                     String host, String port) {
        return new NacosServiceRegistrar(config, serviceRegistrarName, infrastructure, host, parsePort(port));
    }

    /**
     * 解析注册端口，非法值回退 8080。
     *
     * @param value 端口字符串
     * @return 端口整数
     */
    private static int parsePort(String value) {
        try {
            return value == null || value.isBlank() ? 8080 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 8080;
        }
    }
}
