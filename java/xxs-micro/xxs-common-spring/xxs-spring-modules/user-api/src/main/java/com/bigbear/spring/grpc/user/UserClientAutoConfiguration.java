package com.bigbear.spring.grpc.user;

import com.bigbear.spring.grpc.user.UserServiceGrpc.UserServiceBlockingStub;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * User 客户端自动装配。
 *
 * <p>声明向名为 {@code user} 的 gRPC 通道注入阻塞 Stub，并暴露充血 {@link UserClient} Bean。
 * 通道目标由消费方在 {@code spring.grpc.client.channel.user.target} 中配置
 * （如 {@code nacos://bear-admin}）。
 */
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(target = "user", types = UserServiceGrpc.UserServiceBlockingStub.class)
public class UserClientAutoConfiguration {

    /**
     * 暴露 User 充血客户端 Bean。
     *
     * @param stub User 阻塞 Stub（由 Spring gRPC 自动创建）
     * @return User 充血客户端
     */
    @Bean
    public UserClient userClient(UserServiceBlockingStub stub) {
        return new UserClient(stub);
    }
}
