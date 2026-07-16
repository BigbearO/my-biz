# xxs-spring-grpc — Spring gRPC 鉴权透传（devh starter）

基于 **devh `grpc-server-spring-boot-starter` / `grpc-client-spring-boot-starter`**，补齐跨语言四头在 gRPC 链路的透传，并兼容 WebMvc / WebFlux 入站。

依赖 `xxs-spring-auth`（基础工具与 `UserContext`）。

## Maven 引入

```xml
<dependency>
  <groupId>com.bigbear</groupId>
  <artifactId>xxs-spring-grpc</artifactId>
</dependency>
<!-- 由消费方按需引入 devh starter（本模块自动装配依赖它） -->
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-server-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-client-spring-boot-starter</artifactId>
</dependency>
```

## 自动装配

`GrpcAuthAutoConfiguration` 通过 `@ConditionalOnClass(GlobalServerInterceptorRegistry)` **仅在 devh 存在时生效**，注册：

- `AuthServerInterceptor`（`@GrpcGlobalServerInterceptor`）— 服务端 Metadata → `UserContext` + gRPC `Context`
- `AuthClientInterceptor` — 客户端 `Context` / `UserContext` → 出站 Metadata 透传

未引入 devh 时整体跳过，拦截器类仍可直接手动挂载到 `ServerBuilder` / `Channel`。

## 服务端拦截器 `AuthServerInterceptor`

自动注册。入站 gRPC Metadata 四头 → 还原 `LoginUser`：

- 写入 gRPC `Context.Key<LoginUser> USER_KEY`，供本进程内 gRPC 调用链读取；
- 同时写入 `UserContext`（ThreadLocal），供同步（Servlet）业务读取；
- `userId` 为空即匿名放行。

## 客户端拦截器 `AuthClientInterceptor`

从 `AuthServerInterceptor.USER_KEY` 或 `UserContext` 取当前用户，连同入站 `Authorization` / `X-Client-Id`（`UserContext` 暂存）写入出站 Metadata 向下游透传。

```java
@GrpcClient("greeter")
private GreeterGrpc.GreeterBlockingStub greeter;

public void call() {
    // UserContext 在当前请求上下文已有值，出站时自动透传身份
    greeter.hello(HelloRequest.newBuilder().setName("x").build());
}
```

## REST 入站过滤器（WebMvc / WebFlux）

- `AuthServletFilter` — Servlet 容器：入站读 `X-User-Id` / `X-User-Info` 注入 `UserContext`，暂存 `Authorization` / `X-Client-Id`，出站清空。
- `AuthWebFilter` — WebFlux：等价响应式实现。

> 引入方式：把对应 Filter 注册为 Spring Bean 即可（注意与 devh gRPC 服务共存时，两者分别作用于 HTTP 与 gRPC 入站）。

## 业务侧统一获取

```java
import com.bigbear.spring.auth.UserContext;
import com.bigbear.spring.auth.LoginUser;

LoginUser u = UserContext.current();      // null = 匿名
if (UserContext.isAnonymous()) { /* 匿名 */ }
```

## application.yml 配置示例

```yaml
grpc:
  server:
    port: 9090
  client:
    greeter:
      address: 'dns:///grpc-greeter-service:9090'
      negotiation-type: plaintext
```

## 与 xxs-spring-auth 的关系

`UserContext`、`LoginUser`、`AuthHeaders`、`JsonUtils`、`RequestUserUtils` 均位于 `xxs-spring-auth`；本模块只负责**拦截器 / 过滤器 / 自动装配**，把身份在 gRPC / HTTP 链路间透传并读写 `UserContext`。
