package com.bigbear.upms.api.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.Getter;


@Getter
@ApplicationScoped
public class HeaderUtils {

    @Context
    HttpHeaders httpHeaders;

}
