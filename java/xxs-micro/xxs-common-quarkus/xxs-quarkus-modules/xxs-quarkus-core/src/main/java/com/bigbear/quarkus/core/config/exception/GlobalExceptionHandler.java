package com.bigbear.quarkus.core.config.exception;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

//全局异常处理

@Slf4j
public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public Response globalExceptionHandler(Exception e){
        log.error("global exception ", e);
        return Response.status(404).entity("not found").build();
    }

}
