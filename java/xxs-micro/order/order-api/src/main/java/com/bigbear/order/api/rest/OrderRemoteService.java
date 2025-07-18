package com.bigbear.order.api.rest;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.Set;

/**
 * To use it via injection.
 *
 * {@code
 *     @Inject
 *     @RestClient
 *     MyRemoteService myRemoteService;
 *
 *     public void doSomething() {
 *         Set<MyRemoteService.Extension> restClientExtensions = myRemoteService.getExtensionsById("io.quarkus:quarkus-hibernate-validator");
 *     }
 * }
 */
//前缀就是configkey
//# 配置 REST 客户端使用服务发现（而非固定 URL）
//order/mp-rest/url=http://hello-service/api
//order/mp-rest/discovery-enabled=true
//order/mp-rest/service-name=hello-service
//对应模块服务名
@RegisterRestClient(configKey = "order")
//@RegisterRestClient(baseUri =  "stork://my-rest-service)
//以stork:// 开始的baseUri使Stork能够发现服务，并根据负载平衡类型选择一个
public interface OrderRemoteService {

    @GET
    @Path("/extensions")
    Set<String> getExtensionsById(@QueryParam("id") String id);


}
