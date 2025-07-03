package com.bigbear.quarkus.core.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;


@Singleton
@Provider
public class RestRpcFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        // 动态添加请求头，比如从 TokenProvider 获取 token
        String token = IncomingRequestFilter.getCurrentAuthHeader();// 可以是 JWT、OAuth 等
        clientRequestContext.getHeaders().add(CommonConstants.AUTH, "Bearer " + token);
        // 注册清理逻辑，防止内存泄漏（可选）

    }
}
