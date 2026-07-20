package com.bigbear.quarkus.template.provider;

import com.bigbear.quarkus.template.grpc.Greeter;
import com.bigbear.quarkus.template.grpc.HelloReply;
import com.bigbear.quarkus.template.grpc.HelloRequest;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

/**
 * Greeter gRPC 服务实现：演示服务提供者（provider）如何以极低代码量暴露 gRPC 接口。
 * <p>
 * 通过 {@link GrpcService} 注册为 gRPC 服务端点；服务实例会在应用启动时经 Nacos 适配器
 * 自动注册 {@code template-provider}（HTTP）与 {@code template-provider-grpc}（gRPC）两个服务名，
 * 供消费者通过 Stork + Nacos 发现并负载均衡调用。
 *
 * @author xxs-micro
 */
@GrpcService
public class GreeterImpl implements Greeter {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        String name = request.getName() == null || request.getName().isBlank() ? "world" : request.getName();
        String message = "Hello " + name + " from template-provider";
        return Uni.createFrom().item(HelloReply.newBuilder().setMessage(message).build());
    }
}
