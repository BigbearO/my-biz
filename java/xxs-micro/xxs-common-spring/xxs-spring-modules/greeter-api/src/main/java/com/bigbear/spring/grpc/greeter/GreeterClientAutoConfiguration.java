package com.bigbear.spring.grpc.greeter;

import com.bigbear.spring.grpc.greeter.GreeterGrpc.GreeterBlockingStub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * Greeter 客户端自动装配。
 *
 * <p>声明向名为 {@code greeter} 的 gRPC 通道注入阻塞 Stub，并暴露充血 {@link GreeterClient} Bean。
 * 通道目标由消费方在 {@code spring.grpc.client.channel.greeter.target} 中配置
 * （如 {@code nacos://greeter-grpc}）。
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "greeter", types = GreeterGrpc.GreeterBlockingStub.class)
public class GreeterClientAutoConfiguration {

    /**
     * 暴露 Greeter 充血客户端 Bean。
     *
     * @param stub Greeter 阻塞 Stub（由 Spring gRPC 自动创建）
     * @return Greeter 充血客户端
     */
    @Bean
    public GreeterClient greeterClient(GreeterBlockingStub stub) {
        return new GreeterClient(stub);
    }
}
