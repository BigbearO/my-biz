package com.bigbear.spring.grpc.nacos.registrar;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.bigbear.spring.grpc.nacos.properties.NacosNamingProperties;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;

/**
 * gRPC 端口注册器。
 *
 * <p>应用启动后将 gRPC 端口以 {@code <spring.application.name> + serviceSuffix} 为服务名注册到 Nacos；
 * 应用关闭前注销，避免残留无效实例。采用临时实例（ephemeral），随进程下线自动剔除。
 */
public class NacosGrpcRegistrar {

    private static final Logger log = LoggerFactory.getLogger(NacosGrpcRegistrar.class);

    /** Nacos 命名配置。 */
    private final NacosNamingProperties properties;

    /** Nacos 命名服务。 */
    private final NamingService namingService;

    /** 应用名（spring.application.name）。 */
    private final String applicationName;

    /** gRPC 服务端端口（spring.grpc.server.port）。 */
    private final int grpcPort;

    /** 是否已成功注册，用于关闭时精确注销。 */
    private volatile boolean registered = false;

    /**
     * 构造 gRPC 端口注册器。
     *
     * @param properties       Nacos 命名配置
     * @param namingService    Nacos 命名服务
     * @param applicationName  应用名（来自 spring.application.name）
     * @param grpcPort         gRPC 服务端端口（来自 spring.grpc.server.port）
     */
    public NacosGrpcRegistrar(NacosNamingProperties properties, NamingService namingService,
        String applicationName, int grpcPort) {
        this.properties = properties;
        this.namingService = namingService;
        this.applicationName = applicationName;
        this.grpcPort = grpcPort;
    }

    /**
     * 应用启动后注册 gRPC 端口到 Nacos。
     *
     * @param event Spring 应用启动事件
     */
    @EventListener(ApplicationStartedEvent.class)
    public void register(ApplicationStartedEvent event) {
        if (!properties.isServerRegistration() || grpcPort <= 0
            || applicationName == null || applicationName.isEmpty()) {
            return;
        }
        try {
            String serviceName = properties.toGrpcServiceName(applicationName);
            Instance instance = new Instance();
            instance.setIp(resolveLocalIp());
            instance.setPort(grpcPort);
            instance.setClusterName(properties.getCluster());
            instance.setEphemeral(true);
            namingService.registerInstance(serviceName, properties.getGroup(), instance);
            registered = true;
            log.info("已向 Nacos 注册 gRPC 服务[{}] port={}", serviceName, grpcPort);
        } catch (Exception e) {
            log.error("向 Nacos 注册 gRPC 服务失败", e);
        }
    }

    /**
     * 应用关闭前注销 gRPC 实例，避免 Nacos 残留无效地址。
     */
    @PreDestroy
    public void deregister() {
        if (!registered || grpcPort <= 0 || applicationName == null || applicationName.isEmpty()) {
            return;
        }
        try {
            String serviceName = properties.toGrpcServiceName(applicationName);
            namingService.deregisterInstance(serviceName, properties.getGroup(),
                resolveLocalIp(), grpcPort, properties.getCluster());
        } catch (Exception e) {
            log.warn("从 Nacos 注销 gRPC 服务失败", e);
        }
    }

    /**
     * 解析本机 IP（注册到 Nacos 的实例地址）。
     *
     * @return 本机 IP，解析失败时回退 127.0.0.1
     */
    private String resolveLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("解析本机 IP 失败，回退 127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}
