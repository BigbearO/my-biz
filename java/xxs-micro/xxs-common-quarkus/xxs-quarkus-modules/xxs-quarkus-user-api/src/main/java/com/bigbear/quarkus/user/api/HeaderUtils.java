package com.bigbear.quarkus.user.api;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.Getter;


@ApplicationScoped
@Getter
public class HeaderUtils {

    @Context
    HttpHeaders httpHeaders;

    public String getUserId() {
        return httpHeaders.getHeaderString("x-user-id");
    }
}
