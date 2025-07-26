package com.bigbear.quarkus.user.api;


import com.bigbear.quarkus.user.api.dto.UserDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

//使用时使用@RestClient
@RegisterRestClient(baseUri =  "stork://user-service")
public interface RemoteUserService {

    @GET
    @Path("/user/getById")
    UserDTO getUserById(@QueryParam("id") String id);

}
