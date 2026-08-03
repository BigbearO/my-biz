package com.bigbear.spring.grpc.biz;

import com.bigbear.spring.grpc.biz.BizServiceGrpc.BizServiceBlockingStub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * Biz 客户端自动装配。
 *
 * <p>声明向名为 {@code quarkus} 的 gRPC 通道注入阻塞 Stub，并暴露充血 {@link BizClient} Bean。
 * 通道目标由消费方在 {@code spring.grpc.client.channel.quarkus.target} 中配置
 * （如 {@code nacos://template-provider-grpc}）。
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "quarkus", types = BizServiceGrpc.BizServiceBlockingStub.class)
public class BizClientAutoConfiguration {

    /**
     * 暴露 Biz 充血客户端 Bean。
     *
     * @param stub Biz 阻塞 Stub（由 Spring gRPC 自动创建）
     * @return Biz 充血客户端
     */
    @Bean
    public BizClient bizClient(BizServiceBlockingStub stub) {
        return new BizClient(stub);
    }
}
