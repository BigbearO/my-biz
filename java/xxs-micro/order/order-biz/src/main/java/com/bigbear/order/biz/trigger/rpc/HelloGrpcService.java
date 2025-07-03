package com.bigbear.order.biz.trigger.rpc;

import com.bigbear.HelloGrpc;
import com.bigbear.HelloReply;
import com.bigbear.HelloRequest;
import io.grpc.ManagedChannel;
import io.quarkus.grpc.GrpcService;

import io.smallrye.mutiny.Uni;

@GrpcService
public class HelloGrpcService implements HelloGrpc {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        return Uni.createFrom().item("Hello " + request.getName() + "!")
                .map(msg -> HelloReply.newBuilder().setMessage(msg).build());
    }

//    ManagedChannel managedChannel;

}
