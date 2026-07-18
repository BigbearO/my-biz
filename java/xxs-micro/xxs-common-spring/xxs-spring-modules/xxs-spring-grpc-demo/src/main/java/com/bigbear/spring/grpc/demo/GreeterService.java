//package com.bigbear.spring.grpc.demo;
//
//import com.bigbear.spring.auth.LoginUser;
//import com.bigbear.spring.auth.UserContext;
//import io.grpc.stub.StreamObserver;
//import org.springframework.grpc.server.service.GrpcService;
//
///**
// * Greeter gRPC 服务实现：演示服务端拦截器已把入站身份头还原到 {@link UserContext}。
// */
//@GrpcService
//public class GreeterService extends GreeterGrpc.GreeterImplBase {
//
//    /**
//     * SayHello 实现：拼装回显消息，并附带当前登录用户（由 AuthServerInterceptor 注入）。
//     *
//     * @param request           请求（含 name）
//     * @param responseObserver  响应观察者
//     */
//    @Override
//    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
//        LoginUser user = UserContext.current();
//        String who = (user != null && user.userId() != null) ? user.userId() : "anonymous";
//        HelloReply reply = HelloReply.newBuilder()
//            .setMessage("Hello " + request.getName() + ", from user=" + who)
//            .build();
//        responseObserver.onNext(reply);
//        responseObserver.onCompleted();
//    }
//}
