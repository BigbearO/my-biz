package com.bigbear.quarkus.template.consumer;

import com.bigbear.quarkus.template.grpc.Greeter;
import com.bigbear.quarkus.template.grpc.HelloReply;
import com.bigbear.quarkus.template.grpc.HelloRequest;

import io.quarkus.grpc.GrpcClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;

/**
 * 模板消费者 REST 入口：对外暴露 {@code GET /hello?name=xxx}，
 * 内部通过 {@link GrpcClient} 注入的 {@link Greeter}（Mutiny 客户端）以 {@code stork://template-provider-grpc}
 * 经 Nacos 发现 + round_robin 负载均衡调用 provider 的 gRPC 服务，完整跑通“REST → gRPC → Nacos 发现”链路。
 *
 * @author xxs-micro
 */
@Path("/hello")
public class GreeterResource {

    /**
     * gRPC 客户端：由 Quarkus Stork 管理连接，host 指向 stork 服务名 template-provider-grpc。
     */
    @GrpcClient("greeter")
    Greeter greeter;

    /**
     * 问候接口。
     *
     * @param name 用户名（缺省 world）
     * @return provider 返回的问候语
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@QueryParam("name") String name) {
        String finalName = name == null || name.isBlank() ? "world" : name;
        HelloReply reply = greeter.sayHello(HelloRequest.newBuilder().setName(finalName).build())
                .await().atMost(Duration.ofSeconds(5));
        return reply.getMessage();
    }
}
