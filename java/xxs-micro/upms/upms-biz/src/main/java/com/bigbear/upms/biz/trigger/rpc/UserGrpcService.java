package com.bigbear.upms.biz.trigger.rpc;


import com.bigbear.HelloReply;
import com.bigbear.upms.UserGrpc;
import com.bigbear.upms.UserInfo;
import com.bigbear.upms.UserInfoReq;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService implements UserGrpc {


    @Override
    public Uni<UserInfo> getUserInfo(UserInfoReq request) {
        UserInfo userInfo=  UserInfo.newBuilder().setUserName("xxs")
            .build();
        final Uni<UserInfo> item = Uni.createFrom().item(userInfo);
        return item;
    }
}
