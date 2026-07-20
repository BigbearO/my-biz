package com.bigbear.quarkus.nacos.registrar;

import io.smallrye.stork.api.MetadataKey;

/**
 * Nacos 注册元数据键。
 * <p>
 * 用于满足 Stork {@code ServiceRegistrar<MetadataKeyType>} 的泛型约束。Quarkus 注册流程仅透传
 * serviceName/host/port，故本枚举主要服务于非 Quarkus 场景下的元数据携带（协议、安全、健康地址、集群）。
 *
 * @author xxs-micro
 */
public enum NacosMetadataKey implements MetadataKey {

    /** 协议类型：grpc / http */
    PROTOCOL("protocol"),
    /** 是否安全连接 */
    SECURE("secure"),
    /** 健康检查地址 */
    HEALTH_URL("healthUrl"),
    /** 集群名 */
    CLUSTER("cluster");

    private final String name;

    NacosMetadataKey(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
