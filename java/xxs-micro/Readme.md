## 说明

1 这个项目 作为java 通用架构 公共依赖构建，无论是 spring 还是quarkus
项目名带quarkus的，就是quarkus，带spring的是spring依赖

2 之后再把study ，xxs-common 里面那些迁移过来

3 问题：现在想让微服务继承bom,但是bom 有俩，mirco 不可能继承 其中一个
 那就只能像pig 一样，bom 包含其他common 模块，再被mirco 引入
 其他模块不能依赖,继承此bom
 但是好像能嵌套,把common-core弄进去

4 或者可以， bom不被micro引入,只引入micro下的common ，然后作为parent，
其他业务项目都继承这个


 最终方案；
1 spring,quarkus 公共依赖在micro
2 再定义父子项目
   xxs-common-spring  -- todo spring 那些将就ruoyi-plus用 之后再迁移公共模块
   xxs-common-quarkus
   这俩定义为 pig 和 ruoyi 的最顶级父类项目




## grpc
集成grpc
在api模块 src/main/proto 增加proto文件，然后配置插件，自动生成代码
在biz模块， 实现上述文件中 service定义的接口--也可HelloGrpcImplBase
在调用方 加注解 proto 生成的service 接口 例如：
@GrpcClient("greeter")
Greeter  greeter

然后在调用方配置文件配置，

quarkus.grpc.clients.你在GrpcClient注解里面的服务名.host=dns:///grpc-hello-service 
这里面就是  quarkus.grpc.clients.greeter.host=dns:///grpc-hello-service
文档
https://quarkus.io/guides/grpc-service-consumption

### 1 怎么集成 服务发现

gRPC 客户端配置
quarkus.grpc.clients.你的gprc服务名.host=dns:///grpc-hello-service

问ai 
quarkus集成，grpc，并通过consul注册中心实现服务发现





## RestClient 


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

4 自定义Header
在第一节已经说过了，在接口定义上添加@RegisterClientHeaders(RestClientHeaderFactory.class)
这个注解来指定我们自定义Hearder的实现。
参考
public class RestClientHeaderFactory implements ClientHeadersFactory {
    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders, MultivaluedMap<String, String> clientOutgoingHeaders) {
        return incomingHeaders;
    }
}
```



 todo 怎么从注册中心获取地址



## todo 

go-zero能否 配置rpc和http使用同一端口
https://cloud.tencent.com/developer/article/2514784

