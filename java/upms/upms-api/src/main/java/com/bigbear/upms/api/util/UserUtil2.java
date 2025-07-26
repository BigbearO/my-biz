package com.bigbear.upms.api.util;


import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.upms.api.dto.UserDTO;
import jakarta.ws.rs.core.HttpHeaders;


public class UserUtil2 {


    //todo 会不会串？
    // 目前低负载测试没有串，高并发待测试
    public static UserDTO getUserInfo() {
        HeaderUtils bean = QuarkusUtil.getBean(HeaderUtils.class);
        return UserUtil.getUserInfo(bean.getHttpHeaders());
    }


    public static String test() {
        HeaderUtils bean = QuarkusUtil.getBean(HeaderUtils.class);
        HttpHeaders httpHeaders = bean.getHttpHeaders();
        return httpHeaders.getHeaderString(CommonConstants.USER_ID);
    }



}
