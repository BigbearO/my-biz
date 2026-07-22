package com.bigbear.quarkus.nacos.discovery;

import java.util.Map;

import io.smallrye.stork.api.ServiceDiscovery;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.spi.StorkInfrastructure;
import io.smallrye.stork.spi.internal.ServiceDiscoveryLoader;

/**
 * Nacos 服务发现 Loader（Stork 唯一认可的发现发现入口）。
 * <p>
 * Stork 核心在 {@code Stork.initialize} 阶段通过 {@code ServiceLoader.load(ServiceDiscoveryLoader.class)}
 * 按 {@link #type()} 匹配发现器，而非 {@code ServiceDiscoveryProvider}。因此本类作为适配器，
 * 将 Stork 传入的「去掉 {@code service-discovery.} 前缀」的子配置
 * （{@link ConfigWithType#parameters()} 的键为 {@code server-addr}、{@code namespace} 等）
 * 封装为 {@link NacosConfiguration}，委托 {@link NacosServiceDiscoveryProvider} 创建发现器。
 *
 * @author xxs-micro
 */
public class NacosServiceDiscoveryLoader implements ServiceDiscoveryLoader {

    /** Nacos 发现 Provider，负责基于配置创建 {@link NacosServiceDiscovery} */
    private final NacosServiceDiscoveryProvider provider = new NacosServiceDiscoveryProvider();

    /**
     * 返回发现器类型名，Stork 据此匹配 {@code quarkus.stork.<svc>.service-discovery.type=nacos}。
     *
     * @return 固定为 {@code nacos}
     */
    @Override
    public String type() {
        return "nacos";
    }

    /**
     * 创建 Nacos 服务发现器。
     * <p>
     * Stork 传入的是去掉前缀的发现子配置，其 {@code parameters()} 键与
     * {@link NacosConfiguration} 的 getter 一一对应，直接复用该 Map 构造配置即可。
     *
     * @param config               Stork 传入的发现子配置（含 server-addr / namespace 等）
     * @param serviceName          Stork 服务名
     * @param serviceConfig        完整服务配置
     * @param storkInfrastructure  Stork 基础设施
     * @return Nacos 服务发现器
     */
    @Override
    public ServiceDiscovery createServiceDiscovery(ConfigWithType config, String serviceName,
            ServiceConfig serviceConfig, StorkInfrastructure storkInfrastructure) {
        Map<String, String> parameters = config == null ? Map.of() : config.parameters();
        // Stork 传入的 ConfigWithType 键名可能带 parameters. 前缀，剥离后便于 NacosConfiguration 读取。
        Map<String, String> stripped = stripPrefix(parameters);
        return provider.createServiceDiscovery(new NacosConfiguration(stripped), serviceName,
                serviceConfig, storkInfrastructure);
    }

    /**
     * 剥离参数 Map 中的 {@code parameters.} 前缀，便于配置类按逻辑键读取。
     *
     * @param parameters  Stork 传入的参数 Map
     * @return 剥离前缀后的新 Map（原 Map 不变）
     */
    private static Map<String, String> stripPrefix(Map<String, String> parameters) {
        Map<String, String> result = new java.util.HashMap<>(parameters.size());
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            result.put(key.startsWith("parameters.") ? key.substring("parameters.".length()) : key, entry.getValue());
        }
        return result;
    }
}
