package com.bigbear.quarkus.restrpc.config;


import com.bigbear.quarkus.core.constants.CommonConstants;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Singleton
public class RestRpcFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        // 动态添加请求头，比如从 TokenProvider 获取 token
        clientRequestContext.getHeaders().add(CommonConstants.USER_ID, "1");


    }
}
