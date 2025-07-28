package com.bigbear.upms.biz.trigger.rest;

import com.bigbear.upms.api.dto.UserDTO;
import com.bigbear.upms.api.util.HeaderUtils;
import com.bigbear.upms.api.util.UserUtil2;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
public class UserController {


    @Inject
    HeaderUtils header;


    @GET
    @Path("/getById")
    public UserDTO ss(@QueryParam("userId") String userId) {

        try{
            Thread.sleep(3000);
        } catch (InterruptedException e) {

        }
        String test = UserUtil2.test();

        System.out.println(userId+"-"+":"+test);

        UserDTO userDTO = new UserDTO();
        userDTO.setId(userId);
        userDTO.setUsername("111");
        return userDTO;
    }
}
