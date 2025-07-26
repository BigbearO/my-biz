package com.bigbear.order.biz.trigger.rest;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.upms.api.rest.UserRemoteService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/order")
@Produces(MediaType.APPLICATION_JSON)
public class ExampleResource {


    @RestClient
    UserRemoteService userRemoteService;

    @GET
    @Path("/test")
    public String hello(  @Context HttpHeaders httpHeaders) {
        userRemoteService.getUserById("1");
        System.out.println(httpHeaders.getHeaderString(CommonConstants.USER_ID));
        return "122";
    }
}
