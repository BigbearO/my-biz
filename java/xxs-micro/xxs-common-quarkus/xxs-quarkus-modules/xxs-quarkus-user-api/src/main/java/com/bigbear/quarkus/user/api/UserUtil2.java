package com.bigbear.quarkus.user.api;


import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.quarkus.user.api.dto.UserDTO;


public class UserUtil2 {


    //todo 会不会串？
    public static UserDTO getUserInfo() {

        HeaderUtils bean = QuarkusUtil.getBean(HeaderUtils.class);
        return UserUtil.getUserInfo(bean.getHttpHeaders());
    }



}
