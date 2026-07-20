package com.bigbear.quarkus.nacos.discovery;

import java.util.List;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.impl.CachingServiceDiscovery;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.bigbear.quarkus.nacos.client.NacosConnectProperties;
import com.bigbear.quarkus.nacos.client.NacosNamingClient;
import com.bigbear.quarkus.nacos.convert.NacosConvert;

/**
 * Nacos 服务发现实现。
 * <p>
 * 继承 {@link CachingServiceDiscovery} 获得带缓存的发现能力，并在构造期订阅 Nacos 实例变更，
 * 变更时主动 {@code invalidate()} 缓存，使新实例即时生效；同时按 {@code refresh-period} 周期兜底刷新。
 *
 * @author xxs-micro
 */
public class NacosServiceDiscovery extends CachingServiceDiscovery {

    private final String serviceName;
    private final NacosConfiguration config;
    private final NacosNamingClient namingClient;

    /**
     * 构造 Nacos 服务发现。
     *
     * @param serviceName Stork 服务名
     * @param config      Nacos 服务发现配置
     */
    NacosServiceDiscovery(String serviceName, NacosConfiguration config) {
        super(config.getRefreshPeriod());
        this.serviceName = serviceName;
        this.config = config;
        this.namingClient = NacosNamingClient.getInstance();
        String group = resolveGroup();
        NacosConnectProperties props = buildConnectProperties();
        // 订阅 Nacos 实例变更，触发缓存失效，实现实时发现（Nacos 不可用时仅告警，不影响启动）
        namingClient.subscribe(props, resolveApplication(), group, event -> this.invalidate());
    }

    @Override
    public Uni<List<ServiceInstance>> fetchNewServiceInstances(List<ServiceInstance> previousInstances) {
        String group = resolveGroup();
        NacosConnectProperties props = buildConnectProperties();
        List<Instance> instances = namingClient.selectInstances(props, resolveApplication(), group, true);
        return Uni.createFrom().item(() -> instances.stream()
                .map(instance -> NacosConvert.toServiceInstance(instance, previousInstances))
                .toList());
    }

    /**
     * 解析 Nacos 分组（缺省 DEFAULT_GROUP）。
     */
    private String resolveGroup() {
        String group = config.getGroup();
        return group == null || group.isBlank() ? "DEFAULT_GROUP" : group;
    }

    /**
     * 解析实际查询的 Nacos 服务名：优先使用 application 别名，否则用 Stork 服务名。
     */
    private String resolveApplication() {
        String application = config.getApplication();
        return application == null || application.isBlank() ? serviceName : application;
    }

    /**
     * 基于配置构建 Nacos 连接属性。
     */
    private NacosConnectProperties buildConnectProperties() {
        return new NacosConnectProperties(config.getServerAddr(), config.getNamespace(),
                config.getUsername(), config.getPassword());
    }
}
