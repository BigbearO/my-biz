package com.bigbear.upms.biz.trigger.rpc;



import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.grpc.config.MetadataInterceptor;
import com.bigbear.upms.UserGrpc;
import com.bigbear.upms.UserGrpcGrpc;
import com.bigbear.upms.UserInfo;
import com.bigbear.upms.UserInfoReq;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService implements UserGrpc {


    @Override
    public Uni<UserInfo> getUserInfo(UserInfoReq request) {
        System.out.println(111);
        UserInfo userInfo=  UserInfo.newBuilder().setUserName("xxs")
            .build();
        String o = MetadataInterceptor.USER.get();
        Object o2 = Context.key(CommonConstants.CLIENT_ID).get();
        System.out.println("userId:"+o);
        System.out.println("client2:"+o2);

        Uni<UserInfo> item = Uni.createFrom().item(userInfo);
        return item;
    }


//    public static class tes extends   UserGrpcGrpc.UserGrpcImplBase {
//        @Override
//        public void getUserInfo(UserInfoReq request, StreamObserver<UserInfo> responseObserver) {
//            UserInfo build = UserInfo.newBuilder().setUserName("111").build();
//            responseObserver.onNext(build);
//            responseObserver.onCompleted();
//        }
//    }
}
