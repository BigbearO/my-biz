package com.bigbear.spring.grpc.nacos.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Properties;

/**
 * Nacos 命名服务配置属性。
 *
 * <p>绑定前缀 {@code grpc.nacos}，用于构造 Nacos {@code NamingService} 并控制服务注册/发现行为。
 * 服务端按 {@code spring.application.name + serviceSuffix} 注册 gRPC 端口；
 * 客户端按 {@code target: nacos://<serviceName>} 通过自定义 NameResolver 从 Nacos 发现实例。
 *
 * <p>示例（application.yml）：
 * <pre>
 * grpc:
 *   nacos:
 *     enabled: true
 *     server-addr: 127.0.0.1:8848
 *     namespace: dev
 *     server-registration: true
 * </pre>
 */
@ConfigurationProperties(prefix = "grpc.nacos")
public class NacosNamingProperties {

    /** 是否启用 Nacos gRPC 服务发现/注册，默认关闭，需显式开启。 */
    private boolean enabled = false;

    /** Nacos 服务端地址，多个用逗号分隔，如 127.0.0.1:8848。 */
    private String serverAddr;

    /** 命名空间（环境隔离），默认空（public）。 */
    private String namespace = "";

    /** 服务分组，默认 DEFAULT_GROUP。 */
    private String group = "DEFAULT_GROUP";

    /** 集群名，默认 DEFAULT。 */
    private String cluster = "DEFAULT";

    /** Nacos 用户名（开启鉴权时必填）。 */
    private String username;

    /** Nacos 密码。 */
    private String password;

    /** 是否仅返回健康实例，默认 true。 */
    private boolean healthyOnly = true;

    /** 是否订阅实例变更以实现热刷新，默认 true。 */
    private boolean subscribe = true;

    /** 服务端是否注册 gRPC 端口到 Nacos，默认 true。 */
    private boolean serverRegistration = true;

    /** Nacos 服务名后缀，拼接在 spring.application.name 之后，默认 -grpc。 */
    private String serviceSuffix = "-grpc";

    /**
     * 转为 Nacos {@code NamingService} 构造所需的 Properties。
     *
     * @return Nacos 连接属性（serverAddr / namespace / username / password）
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("serverAddr", serverAddr == null ? "" : serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            props.setProperty("namespace", namespace);
        }
        if (username != null && !username.isEmpty()) {
            props.setProperty("username", username);
        }
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        return props;
    }

    /**
     * 拼装 gRPC 服务在 Nacos 中的注册名。
     *
     * @param applicationName 应用名（spring.application.name）
     * @return 拼接后缀后的 Nacos 服务名（如 greeter-grpc）
     */
    public String toGrpcServiceName(String applicationName) {
        return applicationName + serviceSuffix;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isHealthyOnly() {
        return healthyOnly;
    }

    public void setHealthyOnly(boolean healthyOnly) {
        this.healthyOnly = healthyOnly;
    }

    public boolean isSubscribe() {
        return subscribe;
    }

    public void setSubscribe(boolean subscribe) {
        this.subscribe = subscribe;
    }

    public boolean isServerRegistration() {
        return serverRegistration;
    }

    public void setServerRegistration(boolean serverRegistration) {
        this.serverRegistration = serverRegistration;
    }

    public String getServiceSuffix() {
        return serviceSuffix;
    }

    public void setServiceSuffix(String serviceSuffix) {
        this.serviceSuffix = serviceSuffix;
    }
}
