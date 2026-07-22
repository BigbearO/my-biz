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
 * Quarkus 的自动注册 gate 仅覆盖 Consul/Eureka/Static，不触发自定义 {@code nacos} 注册器，
 * 故注册动作由 {@link NacosStorkRegistration} 在应用启动后调用本类的 {@link #registerNow()} 完成；
 * 应用关闭时调用 {@link #deregisterServiceInstance(String)} 注销。
 * 注册 IP/端口在装配期由 {@link NacosServiceRegistrarLoader} 经 {@code service-registrar.parameters.host/port} 透传保存。
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
    /** 注册到 Nacos 的服务名（等同 Stork 注册名） */
    private final String serviceName;
    /** 注册实例 IP（来自 service-registrar.parameters.host） */
    private final String ipAddress;
    /** 注册实例端口（来自 service-registrar.parameters.port） */
    private final int defaultPort;

    /**
     * 构造 Nacos 服务注册（默认 IP/端口）。
     * <p>
     * 供 Stork 原生 Provider 路径复用，缺省 localhost:8080。
     *
     * @param config                Nacos 服务注册配置
     * @param serviceRegistrarName  Stork 注册名
     * @param infrastructure        Stork 基础设施
     */
    public NacosServiceRegistrar(NacosRegistrarConfiguration config, String serviceRegistrarName,
            StorkInfrastructure infrastructure) {
        this(config, serviceRegistrarName, infrastructure, "localhost", 8080);
    }

    /**
     * 构造携带注册 IP/端口的 Nacos 服务注册。
     * <p>
     * IP/端口由 {@link NacosServiceRegistrarLoader} 从 {@code service-registrar.parameters.host/port} 取出并透传，
     * 应用启动后由 {@link NacosStorkRegistration} 调用 {@link #registerNow()} 完成注册。
     *
     * @param config                Nacos 服务注册配置
     * @param serviceRegistrarName  Stork 注册名（同时作为 Nacos 服务名）
     * @param infrastructure        Stork 基础设施
     * @param ipAddress             注册实例 IP
     * @param defaultPort           注册实例端口
     */
    public NacosServiceRegistrar(NacosRegistrarConfiguration config, String serviceRegistrarName,
            StorkInfrastructure infrastructure, String ipAddress, int defaultPort) {
        this.serviceName = serviceRegistrarName;
        this.ipAddress = ipAddress;
        this.defaultPort = defaultPort;
        this.config = config;
        this.namingClient = NacosNamingClient.getInstance();
    }

    /**
     * 应用启动后由 {@link NacosStorkRegistration} 触发，向 Nacos 注册本实例。
     *
     * @return 注册完成的 Uni 信号
     */
    public Uni<Void> registerNow() {
        return registerServiceInstance(serviceName, null, ipAddress, defaultPort);
    }

    /**
     * 返回注册到 Nacos 的服务名。
     *
     * @return 服务名
     */
    public String getServiceName() {
        return serviceName;
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
