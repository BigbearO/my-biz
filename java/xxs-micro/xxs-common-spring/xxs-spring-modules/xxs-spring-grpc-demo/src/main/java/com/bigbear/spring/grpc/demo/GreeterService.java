package com.bigbear.spring.grpc.demo;

import com.bigbear.spring.grpc.greeter.GreeterGrpc;
import com.bigbear.spring.grpc.greeter.HelloReply;
import com.bigbear.spring.grpc.greeter.HelloRequest;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

/**
 * Greeter gRPC 服务实现：演示基于共享契约（greeter-api）暴露 SayHello。
 */
@GrpcService
public class GreeterService extends GreeterGrpc.GreeterImplBase {

    /**
     * SayHello 实现：拼装回显消息。
     *
     * @param request          请求（含 name）
     * @param responseObserver 响应观察者
     */
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
            .setMessage("Hello " + request.getName())
            .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
