//package com.bigbear.spring.grpc.demo;
//
//import org.springframework.grpc.client.ImportGrpcClients;
//import org.springframework.context.annotation.Configuration;
//
///**
// * gRPC 客户端配置：声明向名为 {@code greeter} 的通道注入阻塞 Stub。
// *
// * <p>Stub 由 Spring gRPC 按 {@code spring.grpc.client.channel.greeter.target} 自动创建，
// * 出站时 {@code AuthClientInterceptor} 自动透传当前用户身份。
// */
//@Configuration
//@ImportGrpcClients(target = "greeter", types = GreeterGrpc.GreeterBlockingStub.class)
//public class GrpcClientConfig {
//}
