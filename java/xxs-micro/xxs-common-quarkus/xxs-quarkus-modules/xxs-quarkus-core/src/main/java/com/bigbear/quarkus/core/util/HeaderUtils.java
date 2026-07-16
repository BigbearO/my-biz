package com.bigbear.quarkus.core.util;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.model.LoginUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.Getter;


@Getter
@ApplicationScoped
public class HeaderUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Context
    HttpHeaders httpHeaders;


    public String getUserId() {
        return httpHeaders.getHeaderString(CommonConstants.USER_ID);
    }

    public String getUserInfo() {
        return httpHeaders.getHeaderString(CommonConstants.USER_INFO);
    }

    public String getClientId() {
        return httpHeaders.getHeaderString(CommonConstants.CLIENT_ID);
    }

    /**
     * 从当前 REST 请求头取身份（零解析）：userId 为空视为匿名返回 null。
     */
    public LoginUser getLoginUser() {
        String userId = getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LoginUser user = parseLoginUser(getUserInfo());
        if (user == null) {
            user = new LoginUser();
        }
        if (user.getUserId() == null) {
            user.setUserId(userId);
        }
        return user;
    }

    // ===== 静态 JSON / 解析工具（不依赖 CDI 注入） =====

    public static LoginUser parseLoginUser(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LoginUser.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(LoginUser user) {
        try {
            return MAPPER.writeValueAsString(user);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static <T> T toBean(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T toBean(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            return null;
        }
    }
}
