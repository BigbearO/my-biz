# xxs-spring-grpc — Spring gRPC 鉴权透传（官方原生 spring-grpc）

基于 **Spring Boot 4.1 官方原生 gRPC（`org.springframework.grpc`）**，补齐跨语言四头在 gRPC 链路的透传，并兼容 WebMvc / WebFlux 入站。

依赖 `xxs-spring-auth`（基础工具与 `UserContext`）。

## Maven 引入

```xml
<dependency>
  <groupId>com.bigbear</groupId>
  <artifactId>xxs-spring-grpc</artifactId>
</dependency>
```

`spring-boot-starter-grpc-server` / `spring-boot-starter-grpc-client` 已作为本模块的编译期依赖，
并随 `xxs-spring-grpc` 传递引入，消费方无需再单独声明。gRPC 版本由 `spring-boot-dependencies(4.1.0)` 统一管理。

## 自动装配

`GrpcAuthAutoConfiguration` 零配置开箱即用，按 `@ConditionalOnClass` 隔离 server / client 能力：

- `AuthServerInterceptor`（`@GlobalServerInterceptor`）— 服务端 Metadata → `UserContext` + gRPC `Context`
- `AuthClientInterceptor`（`@GlobalClientInterceptor`）— 客户端 `Context` / `UserContext` → 出站 Metadata 透传

消费方引入 `xxs-spring-grpc` 即自动生效，无需任何额外配置。

## 服务端拦截器 `AuthServerInterceptor`

自动注册。入站 gRPC Metadata 四头 → 还原 `LoginUser`：

- 写入 gRPC `Context.Key<LoginUser> USER_KEY`，供本进程内 gRPC 调用链读取；
- 同时写入 `UserContext`（ThreadLocal），供同步（Servlet）业务读取；
- `userId` 为空即匿名放行。

## 客户端拦截器 `AuthClientInterceptor`

从 `AuthServerInterceptor.USER_KEY` 或 `UserContext` 取当前用户，连同入站 `Authorization` / `X-Client-Id`（`UserContext` 暂存）写入出站 Metadata 向下游透传。

```java
@Configuration
@ImportGrpcClients(target = "greeter",
    types = GreeterGrpc.GreeterBlockingStub.class)
public class GrpcClientConfig {
    // stub 由 Spring gRPC 自动创建为 Bean，出站时自动透传身份
}

@Service
public class SomeService {

    private final GreeterGrpc.GreeterBlockingStub greeter;

    public SomeService(GreeterGrpc.GreeterBlockingStub greeter) {
        this.greeter = greeter;
    }

    public String call() {
        // UserContext 在当前请求上下文已有值，出站时自动透传身份
        return greeter.sayHello(HelloRequest.newBuilder().setName("x").build()).getMessage();
    }
}
```

## REST 入站过滤器（WebMvc / WebFlux）

- `AuthServletFilter` — Servlet 容器：入站读 `X-User-Id` / `X-User-Info` 注入 `UserContext`，暂存 `Authorization` / `X-Client-Id`，出站清空。
- `AuthWebFilter` — WebFlux：等价响应式实现。

> 引入方式：把对应 Filter 注册为 Spring Bean 即可（与 gRPC 服务共存时，两者分别作用于 HTTP 与 gRPC 入站）。

## 业务侧统一获取

```java
import com.bigbear.spring.auth.UserContext;
import com.bigbear.spring.auth.LoginUser;

LoginUser u = UserContext.current();      // null = 匿名
if (UserContext.isAnonymous()) { /* 匿名 */ }
```

## application.yml 配置示例

```yaml
spring:
  grpc:
    server:
      port: 9090
      reflection:
        enabled: false   # 生产关闭反射
    client:
      channel:
        greeter:
          target: static://grpc-greeter-service:9090
          negotiation-type: plaintext   # 开发期无 TLS，生产改用 ssl.bundle
```

## 与 xxs-spring-auth 的关系

`UserContext`、`LoginUser`、`AuthHeaders`、`JsonUtils`、`RequestUserUtils` 均位于 `xxs-spring-auth`；本模块只负责**拦截器 / 过滤器 / 自动装配**，把身份在 gRPC / HTTP 链路间透传并读写 `UserContext`。
