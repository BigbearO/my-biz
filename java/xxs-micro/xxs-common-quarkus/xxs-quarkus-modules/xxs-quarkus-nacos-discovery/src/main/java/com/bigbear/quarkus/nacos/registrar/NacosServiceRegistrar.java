package com.bigbear.quarkus.nacos.registrar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.Metadata;
import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.spi.StorkInfrastructure;

import com.bigbear.quarkus.nacos.client.NacosConnectProperties;
import com.bigbear.quarkus.nacos.client.NacosNamingClient;
import com.bigbear.quarkus.nacos.convert.NacosConvert;

import lombok.extern.slf4j.Slf4j;

/**
 * Nacos 服务注册实现。
 * <p>
 * Quarkus 启动时调用 {@code registerServiceInstance(serviceName, host, port)}（3 参数默认实现会转调本类的 4 参数实现），
 * 完成实例注册；应用关闭时由 Quarkus Stork 调用 {@code deregisterServiceInstance(serviceName)} 注销。
 * 注册时按服务名后缀推断协议（{@code -grpc} 视为 gRPC），写入 Nacos 实例元数据，供发现侧识别。
 *
 * @author xxs-micro
 */
@Slf4j
public class NacosServiceRegistrar implements ServiceRegistrar<NacosMetadataKey> {

    /** 本进程已注册实例缓存，供关闭时按 serviceName 精确注销 */
    private final Map<String, RegisteredInstance> registered = new ConcurrentHashMap<>();

    private final NacosRegistrarConfiguration config;
    private final NacosNamingClient namingClient;

    /**
     * 构造 Nacos 服务注册。
     *
     * @param config                Nacos 服务注册配置
     * @param serviceRegistrarName  Stork 注册名
     * @param infrastructure        Stork 基础设施
     */
    public NacosServiceRegistrar(NacosRegistrarConfiguration config, String serviceRegistrarName,
            StorkInfrastructure infrastructure) {
        this.config = config;
        this.namingClient = NacosNamingClient.getInstance();
    }

    @Override
    public Uni<Void> registerServiceInstance(String serviceName, Metadata<NacosMetadataKey> metadata,
            String ipAddress, int defaultPort) {
        return Uni.createFrom().emitter(em -> {
            try {
                if (ipAddress == null || ipAddress.isBlank()) {
                    em.fail(new IllegalArgumentException("Nacos register failed: ipAddress is null for " + serviceName));
                    return;
                }
                String group = resolveGroup();
                boolean ephemeral = !"false".equalsIgnoreCase(config.getEphemeral());
                double weight = parseWeight(config.getWeight());
                Map<String, String> instanceMetadata = Map.of(
                        "protocol", NacosConvert.resolveProtocol(serviceName),
                        "secure", "false",
                        "cluster", config.getClusterName());
                NacosConnectProperties props = buildConnectProperties();
                namingClient.register(props, serviceName, group, ipAddress, defaultPort,
                        ephemeral, weight, instanceMetadata);
                registered.put(serviceName, new RegisteredInstance(ipAddress, defaultPort));
                em.complete(null);
            } catch (Exception e) {
                em.fail(e);
            }
        });
    }

    @Override
    public Uni<Void> deregisterServiceInstance(String serviceName) {
        return Uni.createFrom().emitter(em -> {
            try {
                RegisteredInstance instance = registered.remove(serviceName);
                if (instance == null) {
                    em.complete(null);
                    return;
                }
                String group = resolveGroup();
                NacosConnectProperties props = buildConnectProperties();
                namingClient.deregister(props, serviceName, group, instance.ip, instance.port);
                em.complete(null);
            } catch (Exception e) {
                em.fail(e);
            }
        });
    }

    /**
     * 解析 Nacos 分组（缺省 DEFAULT_GROUP）。
     */
    private String resolveGroup() {
        String group = config.getGroup();
        return group == null || group.isBlank() ? "DEFAULT_GROUP" : group;
    }

    /**
     * 解析实例权重，非法值回退 1.0。
     */
    private double parseWeight(String value) {
        try {
            return value == null || value.isBlank() ? 1.0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Nacos 实例权重配置非法: {}, 使用默认 1.0", value);
            return 1.0;
        }
    }

    /**
     * 基于配置构建 Nacos 连接属性。
     */
    private NacosConnectProperties buildConnectProperties() {
        return new NacosConnectProperties(config.getServerAddr(), config.getNamespace(),
                config.getUsername(), config.getPassword());
    }

    /**
     * 已注册实例的轻量载体（IP + 端口），用于关闭时精确注销。
     */
    private static final class RegisteredInstance {
        private final String ip;
        private final int port;

        private RegisteredInstance(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
