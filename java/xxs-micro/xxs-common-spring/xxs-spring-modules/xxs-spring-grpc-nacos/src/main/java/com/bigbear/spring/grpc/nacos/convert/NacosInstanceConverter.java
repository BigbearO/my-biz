package com.bigbear.spring.grpc.nacos.convert;

import com.alibaba.nacos.api.naming.pojo.Instance;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nacos 实例 → gRPC 等价地址组转换器。
 *
 * <p>将 Nacos 返回的实例列表过滤（健康/启用）后转换为 gRPC 可消费的等价地址组列表。
 * 采用静态工具类，保持无状态、低耦合。
 */
public final class NacosInstanceConverter {

    /** 私有构造，禁止实例化。 */
    private NacosInstanceConverter() {
    }

    /**
     * 将 Nacos 实例列表转换为 gRPC 等价地址组。
     *
     * @param instances   Nacos 实例列表（可能含不健康实例，允许为 null）
     * @param healthyOnly 是否仅保留健康实例
     * @return gRPC 等价地址组列表（不会为 null，可能为空）
     */
    public static List<EquivalentAddressGroup> toAddressGroups(List<Instance> instances, boolean healthyOnly) {
        List<EquivalentAddressGroup> groups = new ArrayList<>();
        if (instances == null) {
            return groups;
        }
        for (Instance instance : instances) {
            // 提前跳过不健康或未启用的实例，避免无效地址进入负载均衡
            if (healthyOnly && !instance.isHealthy()) {
                continue;
            }
            if (!instance.isEnabled()) {
                continue;
            }
            InetSocketAddress socketAddress = new InetSocketAddress(instance.getIp(), instance.getPort());
            groups.add(new EquivalentAddressGroup(Collections.singletonList(socketAddress), Attributes.EMPTY));
        }
        return groups;
    }
}
