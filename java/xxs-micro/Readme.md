## 说明
待优化

1. 统一proto文件不急
2. 分布式事务 不急 -现在spring和go通了，但是go没有做成公共模块，也没有注册中心，等后续官方支持了再说，并且相关配置文件放在configs下，
3. quarkus seata
3. grpc 限流 不做
4. 服务间鉴权 不做  但是思路是 服务间鉴权模仿pig inner, 拦截器，只是升级一点，加解密嘛，对的上才调用，现在不管
5. 参考这个 ，集成xxlhttps://github.com/naah69/quarkus-xxl-job
6. 把study ，xxs-common 里面那些迁移过来




 最终方案；
1 spring,quarkus 公共依赖在micro
2 再定义父子项目
   xxs-common-spring  -- todo spring 那些将就ruoyi-plus用 之后再迁移公共模块
   xxs-common-quarkus
   这俩定义为 pig 和 ruoyi 的最顶级父类项目



## otel 

1 使用
spring boot  集成otel 只在部署时挂 -javaagent:opentelemetry-javaagent.jar + 几个 OTEL_* 环境变量

 

 

## grpc
集成grpc
在api模块 src/main/proto 增加proto文件，然后配置插件，自动生成代码
在biz模块， 实现上述文件中 service定义的接口--也可HelloGrpcImplBase
在调用方 加注解 proto 生成的service 接口 例如： 
@GrpcClient("greeter")
Greeter  greeter

 

然后在调用方配置文件配置，

gRPC 客户端配置
quarkus.grpc.clients.你的gprc服务名.host=dns:///grpc-hello-service

quarkus.grpc.clients.greeter.load-balancing-policy=round_robin 负载均衡-轮询策略，

dns 是生产，k8s中的，需要改成什么local

目前自定义了nacos 





## RestClient -丢弃弃


就是类似feign的http代理
@RegisterRestClient 

### 1 使用

参考 https://blog.csdn.net/Tong_Rui/article/details/114215480

```

1 基础使用
在接口上增加注解
@RegisterRestClient(configkey)

//前缀就是 configkey,不然要配置全限定类名 org.acme.rest.client.CountriesService
//# 配置 REST 客户端使用服务发现（而非固定 URL），
//order/mp-rest/url=http://hello-service/api
说是下面这个属性为true，将自动轮询选择不同的实例。
//order/mp-rest/discovery-enabled=true
//order/mp-rest/service-name=hello-service

2 这里异步有两种方式 CompletionStage 和Uni<?> 直接改返回值就行

3 multipart 支持
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-multipart</artifactId>
</dependency>
 
```

//  从当前请求中获取请求头数据，从而rpc获取user信息成功
//todo restrpc 不能传递请求头,在filter处理了



 






