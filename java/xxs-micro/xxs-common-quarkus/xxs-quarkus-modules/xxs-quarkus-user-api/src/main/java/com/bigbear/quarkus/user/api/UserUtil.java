package com.bigbear.quarkus.user.api;


import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.quarkus.user.api.dto.UserDTO;
import jakarta.ws.rs.core.HttpHeaders;



public class UserUtil {


    public static UserDTO getUserInfo(HttpHeaders httpHeaders) {
        RemoteUserService bean = QuarkusUtil.getBean(RemoteUserService.class);

        String userId=httpHeaders.getHeaderString(CommonConstants.USER_ID);

        return bean.getUserById(userId);
    }



}
