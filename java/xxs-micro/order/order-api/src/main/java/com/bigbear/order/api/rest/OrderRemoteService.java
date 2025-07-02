package com.bigbear.order.api.rest;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.Set;

/**
 * To use it via injection.
 *
 * {@code
 *     @Inject
 *     @RestClient
 *     MyRemoteService myRemoteService;
 *
 *     public void doSomething() {
 *         Set<MyRemoteService.Extension> restClientExtensions = myRemoteService.getExtensionsById("io.quarkus:quarkus-hibernate-validator");
 *     }
 * }
 */
//todo 怎么从注册中心拿地址
@RegisterRestClient(baseUri = "https://stage.code.quarkus.io/api")
public interface OrderRemoteService {

    @GET
    @Path("/extensions")
    Set<String> getExtensionsById(@QueryParam("id") String id);


}
