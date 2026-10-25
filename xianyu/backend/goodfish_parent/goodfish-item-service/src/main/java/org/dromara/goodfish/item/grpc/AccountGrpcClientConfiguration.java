package org.dromara.goodfish.item.grpc;

import account.v1.AccountServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * 账号服务 gRPC 客户端配置.
 *
 * <p>向名为 {@code account} 的通道注入阻塞 Stub；通道目标由 application.yml 中
 * {@code spring.grpc.client.channel.account.target} 配置（nacos://goodfish-client-meta-service-grpc），
 * 经 xxs-spring-grpc-nacos 的 NameResolver 从 Nacos 发现实例.</p>
 *
 * @author goodfish
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "account", types = AccountServiceGrpc.AccountServiceBlockingStub.class)
public class AccountGrpcClientConfiguration {
}
