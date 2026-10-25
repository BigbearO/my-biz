package org.dromara.goodfish.admin.grpc;

import account.v1.AccountServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * 账号服务 gRPC 客户端配置（客户端元数据服务）.
 *
 * <p>通道目标 {@code nacos://goodfish-client-meta-service-grpc} 由 application.yml 配置，
 * 经 xxs-spring-grpc-nacos 的 NameResolver 从 Nacos 发现实例.</p>
 *
 * @author goodfish
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "account", types = AccountServiceGrpc.AccountServiceBlockingStub.class)
public class AccountGrpcClientConfiguration {
}
