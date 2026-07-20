package com.bigbear.quarkus.nacos.registrar;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.smallrye.stork.api.config.ConfigWithType;

/**
 * Nacos 服务注册的 Stork 配置。
 * <p>
 * 由 {@link NacosServiceRegistrarProvider} 上的 {@code @ServiceRegistrarAttribute} 声明字段，
 * Stork 在运行期实例化本类并注入 {@code stork.<svc>.service-registrar.<attr>} 参数。
 * 连接类参数同样会由 {@code NacosNamingClient} 回退到 {@code quarkus.config.source.nacos.*}。
 *
 * @author xxs-micro
 */
public class NacosRegistrarConfiguration implements ConfigWithType {

    private final Map<String, String> parameters;

    /**
     * 基于参数 Map 创建配置（Stork 运行期使用）。
     *
     * @param params 配置参数，不可为 {@code null}
     */
    public NacosRegistrarConfiguration(Map<String, String> params) {
        parameters = Collections.unmodifiableMap(params);
    }

    /**
     * 默认构造器（Stork 反射需要）。
     */
    public NacosRegistrarConfiguration() {
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

    private NacosRegistrarConfiguration extend(String key, String value) {
        Map<String, String> copy = new HashMap<>(parameters);
        copy.put(key, value);
        return new NacosRegistrarConfiguration(copy);
    }

    /**
     * @return Nacos 服务地址
     */
    public String getServerAddr() {
        return parameters.get("server-addr");
    }

    public NacosRegistrarConfiguration withServerAddr(String value) {
        return extend("server-addr", value);
    }

    /**
     * @return Nacos 命名空间
     */
    public String getNamespace() {
        return parameters.get("namespace");
    }

    public NacosRegistrarConfiguration withNamespace(String value) {
        return extend("namespace", value);
    }

    /**
     * @return Nacos 分组
     */
    public String getGroup() {
        return parameters.get("group");
    }

    public NacosRegistrarConfiguration withGroup(String value) {
        return extend("group", value);
    }

    /**
     * @return Nacos 鉴权用户名
     */
    public String getUsername() {
        return parameters.get("username");
    }

    public NacosRegistrarConfiguration withUsername(String value) {
        return extend("username", value);
    }

    /**
     * @return Nacos 鉴权密码
     */
    public String getPassword() {
        return parameters.get("password");
    }

    public NacosRegistrarConfiguration withPassword(String value) {
        return extend("password", value);
    }

    /**
     * @return Nacos 集群名，默认 DEFAULT
     */
    public String getClusterName() {
        String result = parameters.get("cluster-name");
        return result == null || result.isBlank() ? "DEFAULT" : result;
    }

    public NacosRegistrarConfiguration withClusterName(String value) {
        return extend("cluster-name", value);
    }

    /**
     * @return 实例权重，默认 1.0
     */
    public String getWeight() {
        String result = parameters.get("weight");
        return result == null || result.isBlank() ? "1.0" : result;
    }

    public NacosRegistrarConfiguration withWeight(String value) {
        return extend("weight", value);
    }

    /**
     * @return 是否为临时实例（依赖客户端心跳），默认 true
     */
    public String getEphemeral() {
        String result = parameters.get("ephemeral");
        return result == null || result.isBlank() ? "true" : result;
    }

    public NacosRegistrarConfiguration withEphemeral(String value) {
        return extend("ephemeral", value);
    }
}
