package com.bigbear.upms.api.util;


import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.upms.api.dto.UserDTO;
import com.bigbear.upms.api.rest.UserRemoteService;
import jakarta.ws.rs.core.HttpHeaders;


public class UserUtil {


    public static UserDTO getUserInfo(HttpHeaders httpHeaders) {
        UserRemoteService bean = QuarkusUtil.getBean(UserRemoteService.class);
        String userId = httpHeaders.getHeaderString(CommonConstants.USER_ID);
        return bean.getUserById(userId);
    }

//    public static UserDTO getUserInfo() {
//        UserRemoteService bean = QuarkusUtil.getBean(UserRemoteService.class);
//        HttpHeaders httpHeaders = ResteasyContext.getContextData(HttpHeaders.class);
//        String userId = httpHeaders.getHeaderString(CommonConstants.USER_ID);
//        return bean.getUserById(userId);
//    }


}
