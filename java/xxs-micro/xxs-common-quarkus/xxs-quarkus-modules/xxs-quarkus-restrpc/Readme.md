参考下面使用服务发现后自动负载均衡
# 启用服务发现
quarkus.rest-client.my-service-api.url=http://my-service-host:8080
quarkus.rest-client."my-service-api".scope=javax.inject.Singleton

# 配置负载均衡策略
quarkus.rest-client.my-service-api.load-balancer-policy=round-robin

# 使用Consul服务发现
quarkus.rest-client.my-service-api.service-id=my-service
quarkus.consul.service-discovery.enabled=true
quarkus.consul.agent.host=localhost
quarkus.consul.agent.port=8500


//@RegisterRestClient(baseUri =  "stork://my-rest-service)
//以stork:// 开始的baseUri使Stork能够发现服务，并根据负载平衡类型选择一个

# todo
自动传播头信息
配置自动传播的请求头：

properties
quarkus.rest-client.header-propagation.enabled=true
quarkus.rest-client.header-propagation.propagate-headers=X-Forwarded-For,X-Request-ID
