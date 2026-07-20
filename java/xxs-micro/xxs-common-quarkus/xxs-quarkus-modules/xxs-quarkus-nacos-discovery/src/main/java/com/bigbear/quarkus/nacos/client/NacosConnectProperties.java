package com.bigbear.quarkus.nacos.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Nacos 连接属性（充血传输对象）。
 * <p>
 * 承载 {@code serverAddr / namespace / username / password}。任一字段为空时，
 * {@link NacosNamingClient} 会自动回退到官方配置中心扩展 {@code quarkus.config.source.nacos.*} 的配置，
 * 从而实现“Nacos 连接信息一处配置、注册与发现共用”。
 *
 * @author xxs-micro
 */
@Getter
@AllArgsConstructor
public class NacosConnectProperties {

    /**
     * Nacos 服务地址，例如 127.0.0.1:8848
     */
    private final String serverAddr;

    /**
     * 命名空间，默认 public
     */
    private final String namespace;

    /**
     * 用户名（开启鉴权时填写）
     */
    private final String username;

    /**
     * 密码
     */
    private final String password;
}
