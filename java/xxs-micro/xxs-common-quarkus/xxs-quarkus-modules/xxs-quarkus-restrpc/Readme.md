参考下面使用服务发现后自动负载均衡
# 启用服务发现
quarkus.rest-client.my-service-api.url=http://my-service-host:8080
quarkus.rest-client."my-service-api".scope=javax.inject.Singleton
"http://quarkus-member-system.default"

 
