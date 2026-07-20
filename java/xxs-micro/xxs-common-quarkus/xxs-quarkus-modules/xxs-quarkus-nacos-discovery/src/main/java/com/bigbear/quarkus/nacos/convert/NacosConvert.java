package com.bigbear.quarkus.nacos.convert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.smallrye.stork.api.Metadata;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.impl.DefaultServiceInstance;
import io.smallrye.stork.utils.ServiceInstanceIds;

import com.alibaba.nacos.api.naming.pojo.Instance;

/**
 * Nacos 与 Stork 模型互转工具类（纯静态方法，无状态）。
 * <p>
 * 负责 {@link Instance}（Nacos）与 {@link ServiceInstance}（Stork）之间的字段映射，
 * 以及根据服务名推断协议类型（服务名以 {@code -grpc} 结尾视为 gRPC，其余视为 HTTP）。
 *
 * @author xxs-micro
 */
public final class NacosConvert {

    /** gRPC 服务名后缀，用于区分协议 */
    private static final String GRPC_SUFFIX = "-grpc";

    private NacosConvert() {
    }

    /**
     * 将 Nacos 实例转换为 Stork 服务实例。
     * <p>
     * 为保持负载均衡器实例稳定性，若 {@code previous} 中已存在同 {@code ip:port} 的实例则复用其 id，否则生成新 id。
     * Nacos 实例元数据作为 Stork 实例 labels 透出，便于调试与路由。
     *
     * @param instance  Nacos 实例（不可为 {@code null}）
     * @param previous  上一次发现的实例列表（可为 {@code null}）
     * @return Stork 服务实例
     */
    public static ServiceInstance toServiceInstance(Instance instance, List<ServiceInstance> previous) {
        long id = resolveInstanceId(previous, instance.getIp(), instance.getPort());
        Map<String, String> labels = instance.getMetadata() != null ? instance.getMetadata() : Collections.emptyMap();
        return new DefaultServiceInstance(id, instance.getIp(), instance.getPort(), false, labels, Metadata.empty());
    }

    /**
     * 根据服务名推断协议：以 {@code -grpc} 结尾返回 grpc，否则返回 http。
     *
     * @param serviceName 服务名
     * @return 协议类型（grpc / http）
     */
    public static String resolveProtocol(String serviceName) {
        return serviceName != null && serviceName.endsWith(GRPC_SUFFIX) ? "grpc" : "http";
    }

    /**
     * 在已有实例列表中查找相同 ip:port 的实例 id，实现实例 id 稳定；找不到则返回新 id。
     *
     * @param previous 上一次实例列表
     * @param ip       实例 IP
     * @param port     实例端口
     * @return 稳定的实例 id
     */
    private static long resolveInstanceId(List<ServiceInstance> previous, String ip, int port) {
        if (previous != null) {
            for (ServiceInstance si : previous) {
                if (si.getHost().equals(ip) && si.getPort() == port) {
                    return si.getId();
                }
            }
        }
        return ServiceInstanceIds.next();
    }
}
