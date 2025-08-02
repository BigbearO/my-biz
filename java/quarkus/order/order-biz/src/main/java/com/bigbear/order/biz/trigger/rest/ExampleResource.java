package com.bigbear.order.biz.trigger.rest;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.upms.UserGrpc;
import com.bigbear.upms.UserInfo;
import com.bigbear.upms.UserInfoReq;
import com.bigbear.upms.api.rest.UserRemoteService;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/order")
@Produces(MediaType.APPLICATION_JSON)
public class ExampleResource {


    @RestClient
    UserRemoteService userRemoteService;

    @GrpcClient("userGrpc")
    UserGrpc userGrpc;

    @GET
    @Path("/test")
    public String hello(  @Context HttpHeaders httpHeaders) {
        userRemoteService.getUserById("1");
        System.out.println(httpHeaders.getHeaderString(CommonConstants.USER_ID));
        return "122";
    }

    @GET
    @Path("/grpc")
    public String grpc(@Context HttpHeaders httpHeaders) {

        System.out.println(httpHeaders.getHeaderString(CommonConstants.USER_ID));

        UserInfo item = userGrpc.getUserInfo(UserInfoReq.newBuilder().setUserId(1L).build())
            .subscribe().withSubscriber(
                UniAssertSubscriber.create())
            .assertSubscribed()
            .awaitItem(Duration.ofSeconds(10))
            .getItem();

        System.out.println(item.getUserName());

        return item.getUserName();
    }
}
