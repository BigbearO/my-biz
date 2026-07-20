package com.bigbear.quarkus.nacos.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.smallrye.stork.api.config.ConfigWithType;

/**
 * Nacos 服务发现的 Stork 配置。
 * <p>
 * 由 {@link NacosServiceDiscoveryProvider} 上的 {@code @ServiceDiscoveryAttribute} 声明字段，
 * Stork 在运行期负责实例化本类并将 {@code stork.<service>.service-discovery.<attr>} 注入 {@code parameters}。
 * 连接类参数（server-addr/namespace 等）若未显式配置，运行时会由
 * {@code NacosNamingClient} 回退到官方配置中心扩展 {@code quarkus.config.source.nacos.*}。
 *
 * @author xxs-micro
 */
public class NacosConfiguration implements ConfigWithType {

    private final Map<String, String> parameters;

    /**
     * 基于参数 Map 创建配置（Stork 运行期使用）。
     *
     * @param params 配置参数，不可为 {@code null}
     */
    public NacosConfiguration(Map<String, String> params) {
        parameters = Collections.unmodifiableMap(params);
    }

    /**
     * 默认构造器（Stork 反射需要）。
     */
    public NacosConfiguration() {
        parameters = Collections.emptyMap();
    }

    @Override
    public String type() {
        return "nacos";
    }

    @Override
    public Map<String, String> parameters() {
        return parameters;
    }

    private NacosConfiguration extend(String key, String value) {
        Map<String, String> copy = new HashMap<>(parameters);
        copy.put(key, value);
        return new NacosConfiguration(copy);
    }

    /**
     * @return Nacos 服务地址，例如 127.0.0.1:8848
     */
    public String getServerAddr() {
        return parameters.get("server-addr");
    }

    public NacosConfiguration withServerAddr(String value) {
        return extend("server-addr", value);
    }

    /**
     * @return Nacos 命名空间
     */
    public String getNamespace() {
        return parameters.get("namespace");
    }

    public NacosConfiguration withNamespace(String value) {
        return extend("namespace", value);
    }

    /**
     * @return Nacos 分组
     */
    public String getGroup() {
        return parameters.get("group");
    }

    public NacosConfiguration withGroup(String value) {
        return extend("group", value);
    }

    /**
     * @return 服务发现缓存刷新周期，默认 5M
     */
    public String getRefreshPeriod() {
        String result = parameters.get("refresh-period");
        return result == null ? "5M" : result;
    }

    public NacosConfiguration withRefreshPeriod(String value) {
        return extend("refresh-period", value);
    }

    /**
     * @return 是否使用 TLS 加密（可选）
     */
    public String getSecure() {
        return parameters.get("secure");
    }

    public NacosConfiguration withSecure(String value) {
        return extend("secure", value);
    }

    /**
     * @return Nacos 服务名别名；为空时使用 Stork 服务名
     */
    public String getApplication() {
        return parameters.get("application");
    }

    public NacosConfiguration withApplication(String value) {
        return extend("application", value);
    }

    /**
     * @return Nacos 鉴权用户名（可选）
     */
    public String getUsername() {
        return parameters.get("username");
    }

    public NacosConfiguration withUsername(String value) {
        return extend("username", value);
    }

    /**
     * @return Nacos 鉴权密码（可选）
     */
    public String getPassword() {
        return parameters.get("password");
    }

    public NacosConfiguration withPassword(String value) {
        return extend("password", value);
    }
}
