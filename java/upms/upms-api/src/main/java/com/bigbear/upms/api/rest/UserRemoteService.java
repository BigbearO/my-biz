package com.bigbear.upms.api.rest;

import com.bigbear.quarkus.restrpc.config.MyHeadersFactory;
import com.bigbear.upms.api.dto.UserDTO;
import com.bigbear.upms.api.util.HeaderUtils;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
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

//@RegisterRestClient(baseUri =  "stork://upms")
@RegisterRestClient(baseUri =  "http://localhost:8899")
public interface UserRemoteService {


    @GET
    @Path("/user/getById")
    UserDTO getUserById(@QueryParam("userId") String userId);


}
