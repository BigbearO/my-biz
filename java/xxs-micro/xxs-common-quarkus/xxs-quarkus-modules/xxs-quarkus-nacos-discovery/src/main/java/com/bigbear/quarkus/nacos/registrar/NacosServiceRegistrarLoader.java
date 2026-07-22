package com.bigbear.quarkus.nacos.registrar;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.spi.StorkInfrastructure;
import io.smallrye.stork.spi.internal.ServiceRegistrarLoader;

/**
 * Nacos 服务注册 Loader（Stork 唯一认可的注册发现入口）。
 * <p>
 * Stork 核心在 {@code Stork.initialize} 阶段通过 {@code ServiceLoader.load(ServiceRegistrarLoader.class)}
 * 按 {@link #type()} 匹配注册器，而非 {@code ServiceRegistrarProvider}。因此本类作为适配器，
 * 将 Stork 传入的「去掉 {@code service-registrar.} 前缀」的子配置
 * （{@link ConfigWithType#parameters()} 的键为 {@code server-addr}、{@code namespace} 等）
 * 封装为 {@link NacosRegistrarConfiguration}，委托 {@link NacosServiceRegistrarProvider} 创建注册器。
 *
 * @author xxs-micro
 */
public class NacosServiceRegistrarLoader implements ServiceRegistrarLoader<NacosMetadataKey> {

    /** Nacos 注册 Provider，负责基于配置创建 {@link NacosServiceRegistrar} */
    private final NacosServiceRegistrarProvider provider = new NacosServiceRegistrarProvider();

    /** 匹配 Quarkus 占位符 {@code ${key}} 或 {@code ${key:default}} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 返回注册器类型名，Stork 据此匹配 {@code quarkus.stork.<svc>.service-registrar.type=nacos}。
     *
     * @return 固定为 {@code nacos}
     */
    @Override
    public String type() {
        return "nacos";
    }

    /**
     * 创建 Nacos 服务注册器。
     * <p>
     * Stork 传入的 {@code parameters()} 不会经过 Quarkus 占位符展开，{@code host/port} 可能仍以
     * {@code ${quarkus.http.port}} 形式存在，故在此解析为真实值后透传给注册器，
     * 由 {@link NacosStorkRegistration} 在启动时统一触发 {@code registerNow()}。
     *
     * @param config                Stork 传入的注册子配置（含 server-addr / namespace 等）
     * @param serviceRegistrarName  注册名
     * @param infrastructure        Stork 基础设施
     * @return Nacos 服务注册器
     */
    @Override
    public ServiceRegistrar<NacosMetadataKey> createServiceRegistrar(ConfigWithType config,
            String serviceRegistrarName, StorkInfrastructure infrastructure) {
        Map<String, String> parameters = config == null ? Map.of() : config.parameters();
        // Stork 传入的 ConfigWithType 中键名可能带 parameters. 前缀，剥离后便于 NacosRegistrarConfiguration
        // 直接按 server-addr / namespace 等逻辑键读取。
        Map<String, String> stripped = stripPrefix(parameters);
        // 注册 IP/端口来自 service-registrar.parameters.host/port；解析 Quarkus 占位符后透传。
        String rawHost = readParam(parameters, "host", "localhost");
        String rawPort = readParam(parameters, "port", "8080");
        String host = resolvePlaceholder(rawHost);
        String port = resolvePlaceholder(rawPort);
        return provider.createServiceRegistrar(new NacosRegistrarConfiguration(stripped),
                serviceRegistrarName, infrastructure, host, port);
    }

    /**
     * 解析 Quarkus 配置占位符 {@code ${key}} 或 {@code ${key:default}}。
     * <p>
     * Stork {@code parameters} 未做占位符展开，遇到 {@code ${...}} 时通过 MicroProfile Config 取真实值；
     * 普通值（已是真实 IP/端口）原样返回。解析失败回退至占位符中的默认值。
     *
     * @param value 原始参数值（可能为占位符）
     * @return 解析后的真实值
     */
    private static String resolvePlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        Matcher matcher = PLACEHOLDER.matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        String expression = matcher.group(1);
        String key = expression;
        String defaultVal = null;
        int colon = expression.indexOf(':');
        if (colon >= 0) {
            key = expression.substring(0, colon);
            defaultVal = expression.substring(colon + 1);
        }
        try {
            return ConfigProvider.getConfig().getValue(key, String.class);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * 读取注册参数，兼容 Stork 传入的「带/不带 parameters. 前缀」两种键名。
     *
     * @param parameters  Stork 传入的参数 Map
     * @param key         逻辑参数名（如 host / port）
     * @param defaultVal  取不到时的默认值
     * @return 参数值或默认值
     */
    private static String readParam(Map<String, String> parameters, String key, String defaultVal) {
        String value = parameters.get("parameters." + key);
        if (value == null) {
            value = parameters.get(key);
        }
        return value == null ? defaultVal : value;
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
