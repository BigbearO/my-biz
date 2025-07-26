package com.bigbear.quarkus.restrpc.util;

import com.bigbear.quarkus.core.constants.CommonConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.Getter;


@Getter
@ApplicationScoped
public class HeaderUtils {

    @Context
    HttpHeaders httpHeaders;


    public String getUserId() {
        return httpHeaders.getHeaderString(CommonConstants.USER_ID);
    }

}
