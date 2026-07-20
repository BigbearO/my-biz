package com.bigbear.quarkus.nacos.client;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;

import lombok.extern.slf4j.Slf4j;

/**
 * Nacos 命名服务客户端充血封装。
 * <p>
 * 职责：负责 {@link NamingService} 连接的创建与按维度缓存，以及服务实例的注册、注销、健康查询与变更订阅。
 * 连接按 {@code serverAddr + namespace} 维度缓存为单例，避免重复建连；连接参数解析优先级为：
 * <b>显式配置 &gt; 官方配置中心扩展 {@code quarkus.config.source.nacos.*} &gt; 内置默认值</b>，
 * 从而做到“Nacos 连接信息一处配置、注册与发现共用”。
 *
 * @author xxs-micro
 */
@Slf4j
public final class NacosNamingClient {

    /** 客户端单例 */
    private static final NacosNamingClient INSTANCE = new NacosNamingClient();

    /** 与官方 quarkus-config-nacos 扩展同源的连接配置键 */
    private static final String CFG_SERVER_ADDR = "quarkus.config.source.nacos.server-addr";
    private static final String CFG_NAMESPACE = "quarkus.config.source.nacos.namespace";
    private static final String CFG_USERNAME = "quarkus.config.source.nacos.username";
    private static final String CFG_PASSWORD = "quarkus.config.source.nacos.password";
    private static final String DEFAULT_SERVER_ADDR = "127.0.0.1:8848";
    private static final String DEFAULT_NAMESPACE = "public";

    /** 按 serverAddr|namespace 缓存 NamingService，避免重复建连 */
    private final ConcurrentHashMap<String, NamingService> namingServiceCache = new ConcurrentHashMap<>();

    private NacosNamingClient() {
    }

    /**
     * 获取 Nacos 命名服务客户端单例。
     *
     * @return Nacos 命名服务客户端单例
     */
    public static NacosNamingClient getInstance() {
        return INSTANCE;
    }

    /**
     * 注册服务实例到 Nacos（临时实例 + 客户端心跳）。
     *
     * @param props       Nacos 连接属性（serverAddr/namespace 可为空，内部回退到配置中心扩展配置）
     * @param serviceName 服务名
     * @param group       分组
     * @param ip          实例 IP
     * @param port        实例端口
     * @param ephemeral   是否为临时实例（true 表示依赖客户端心跳保活）
     * @param weight      实例权重
     * @param metadata    实例元数据（建议携带 protocol 等标识）
     */
    public void register(NacosConnectProperties props, String serviceName, String group,
            String ip, int port, boolean ephemeral, double weight, Map<String, String> metadata) {
        try {
            NamingService namingService = connect(props);
            Instance instance = new Instance();
            instance.setServiceName(serviceName);
            instance.setIp(ip);
            instance.setPort(port);
            instance.setEphemeral(ephemeral);
            instance.setWeight(weight);
            instance.setMetadata(metadata);
            namingService.registerInstance(serviceName, group, instance);
            log.info("Nacos 注册成功: service={}, group={}, address={}:{}", serviceName, group, ip, port);
        } catch (NacosException e) {
            log.error("Nacos 注册失败: service={}, address={}:{}", serviceName, ip, port, e);
            throw new IllegalStateException("Failed to register instance to Nacos: " + serviceName, e);
        }
    }

    /**
     * 从 Nacos 注销服务实例（应用关闭时由 Quarkus Stork 自动触发）。
     *
     * @param props       Nacos 连接属性
     * @param serviceName 服务名
     * @param group       分组
     * @param ip          实例 IP
     * @param port        实例端口
     */
    public void deregister(NacosConnectProperties props, String serviceName, String group,
            String ip, int port) {
        try {
            NamingService namingService = connect(props);
            Instance instance = new Instance();
            instance.setServiceName(serviceName);
            instance.setIp(ip);
            instance.setPort(port);
            namingService.deregisterInstance(serviceName, group, instance);
            log.info("Nacos 注销成功: service={}, address={}:{}", serviceName, ip, port);
        } catch (NacosException e) {
            log.error("Nacos 注销失败: service={}, address={}:{}", serviceName, ip, port, e);
            throw new IllegalStateException("Failed to deregister instance from Nacos: " + serviceName, e);
        }
    }

    /**
     * 查询服务实例列表（支持仅返回健康实例）。
     *
     * @param props       Nacos 连接属性
     * @param serviceName 服务名
     * @param group       分组
     * @param healthyOnly 是否仅返回健康实例
     * @return Nacos 实例列表（不可为 {@code null}）
     */
    public List<Instance> selectInstances(NacosConnectProperties props, String serviceName,
            String group, boolean healthyOnly) {
        try {
            NamingService namingService = connect(props);
            List<Instance> instances = namingService.selectInstances(serviceName, group, healthyOnly);
            return instances == null ? List.of() : instances;
        } catch (NacosException e) {
            log.error("Nacos 查询实例失败: service={}", serviceName, e);
            throw new IllegalStateException("Failed to discover instances from Nacos: " + serviceName, e);
        }
    }

    /**
     * 订阅服务实例变更，用于主动失效 Stork 缓存，实现变更实时生效。
     *
     * @param props     Nacos 连接属性
     * @param serviceName 服务名
     * @param group     分组
     * @param listener 变更监听器（如回调中调用 {@code CachingServiceDiscovery#invalidate()}）
     */
    public void subscribe(NacosConnectProperties props, String serviceName, String group,
            EventListener listener) {
        try {
            NamingService namingService = connect(props);
            namingService.subscribe(serviceName, group, listener);
            log.debug("已订阅 Nacos 服务变更: service={}, group={}", serviceName, group);
        } catch (Exception e) {
            log.warn("订阅 Nacos 服务变更失败（不影响启动）: service={}", serviceName, e);
        }
    }

    /**
     * 获取（或按连接维度缓存创建）NamingService。
     *
     * @param props Nacos 连接属性
     * @return 已建立的 NamingService 实例
     */
    private NamingService connect(NacosConnectProperties props) {
        String serverAddr = resolve(props.getServerAddr(), CFG_SERVER_ADDR, DEFAULT_SERVER_ADDR);
        String namespace = resolve(props.getNamespace(), CFG_NAMESPACE, DEFAULT_NAMESPACE);
        String username = resolve(props.getUsername(), CFG_USERNAME, null);
        String password = resolve(props.getPassword(), CFG_PASSWORD, null);
        String cacheKey = serverAddr + "|" + namespace;
        return namingServiceCache.computeIfAbsent(cacheKey,
                key -> createNamingService(serverAddr, namespace, username, password));
    }

    /**
     * 基于连接属性创建 NamingService。
     *
     * @param serverAddr 服务地址
     * @param namespace  命名空间
     * @param username   用户名
     * @param password   密码
     * @return 新建的 NamingService
     */
    private NamingService createNamingService(String serverAddr, String namespace,
            String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", namespace);
        if (username != null && !username.isBlank()) {
            properties.setProperty("username", username);
        }
        if (password != null && !password.isBlank()) {
            properties.setProperty("password", password);
        }
        try {
            return NacosFactory.createNamingService(properties);
        } catch (NacosException e) {
            throw new IllegalStateException("Failed to create Nacos NamingService: " + serverAddr, e);
        }
    }

    /**
     * 连接参数解析：显式配置优先，缺失时回退到官方配置中心扩展配置，最后使用默认值。
     *
     * @param configured  显式配置值
     * @param configKey   配置中心扩展回退键
     * @param defaultVal  最终默认值
     * @return 解析后的连接参数
     */
    private String resolve(String configured, String configKey, String defaultVal) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ConfigProvider.getConfig()
                .getOptionalValue(configKey, String.class)
                .orElse(defaultVal);
    }
}
