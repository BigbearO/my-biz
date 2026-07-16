package com.bigbear.quarkus.core.filter;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.context.UserContext;
import com.bigbear.quarkus.core.model.LoginUser;
import com.bigbear.quarkus.core.util.HeaderUtils;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * REST 入站过滤器：从网关注入的身份头构建 {@link LoginUser} 并放入 {@link UserContext}。
 * 与 gRPC 服务端拦截器共用同一 {@code UserContext}，业务侧取身份 API 统一。
 */
@Provider
public class AuthContainerRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String userId = requestContext.getHeaderString(CommonConstants.USER_ID);
        if (userId == null || userId.isBlank()) {
            UserContext.set(null);
            return;
        }
        LoginUser user = HeaderUtils.parseLoginUser(
            requestContext.getHeaderString(CommonConstants.USER_INFO));
        if (user == null) {
            user = new LoginUser();
        }
        if (user.getUserId() == null) {
            user.setUserId(userId);
        }
        UserContext.set(user);
    }
}
