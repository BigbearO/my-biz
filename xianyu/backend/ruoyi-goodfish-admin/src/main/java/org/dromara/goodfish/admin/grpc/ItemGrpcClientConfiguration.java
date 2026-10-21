package org.dromara.goodfish.admin.grpc;

import item.v1.ItemServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * 商品服务 gRPC 客户端配置.
 *
 * <p>通道目标 {@code nacos://goodfish-item-service-grpc} 由 application.yml 配置,
 * 经 xxs-spring-grpc-nacos 的 NameResolver 从 Nacos 发现实例.</p>
 *
 * @author goodfish
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "item", types = ItemServiceGrpc.ItemServiceBlockingStub.class)
public class ItemGrpcClientConfiguration {
}
