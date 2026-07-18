//package com.bigbear.spring.grpc.demo;
//
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * REST 入口：模拟网关已注入身份头（X-User-Id / X-User-Info）的下游服务。
// *
// * <p>请求经 {@code AuthServletFilter} 注入 {@link com.bigbear.spring.auth.UserContext}，
// * 再通过 {@code greeter} 通道调用本服务的 gRPC 实现，验证身份全程透传。
// */
//@RestController
//@RequestMapping("/demo")
//public class GreeterController {
//
//    private final GreeterGrpc.GreeterBlockingStub greeterStub;
//
//    /**
//     * 构造器注入 gRPC 阻塞 Stub。
//     *
//     * @param greeterStub Greeter 阻塞 Stub（由 {@link GrpcClientConfig} 创建）
//     */
//    public GreeterController(GreeterGrpc.GreeterBlockingStub greeterStub) {
//        this.greeterStub = greeterStub;
//    }
//
//    /**
//     * 调用本地 gRPC Greeter 服务并打印回显消息。
//     *
//     * @param name 问候对象，默认 world
//     * @return gRPC 服务回显的消息
//     */
//    @GetMapping("/hello")
//    public String hello(@RequestParam(defaultValue = "world") String name) {
//        return greeterStub.sayHello(HelloRequest.newBuilder().setName(name).build()).getMessage();
//    }
//}
