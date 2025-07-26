package com.bigbear.quarkus.restrpc.config;

import com.bigbear.quarkus.core.constants.CommonConstants;
import com.bigbear.quarkus.core.util.QuarkusUtil;
import com.bigbear.quarkus.restrpc.util.HeaderUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

@ApplicationScoped
public class MyHeadersFactory implements ClientHeadersFactory {

    @Override
    public MultivaluedMap<String, String> update(
        MultivaluedMap<String, String> incomingHeaders,
        MultivaluedMap<String, String> clientOutgoingHeaders) {

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        // 从当前请求复制头信息
        headers.putAll(incomingHeaders);
        // 添加额外头
        HeaderUtils bean = QuarkusUtil.getBean(HeaderUtils.class);
        String userId = bean.getUserId();
        headers.add(CommonConstants.USER_ID,userId );
        return headers;
    }
}
