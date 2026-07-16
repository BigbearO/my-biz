# xxs-quarkus-grpc — gRPC 鉴权透传拦截器

配合 `xxs-quarkus-core` 的 `UserContext`，在 gRPC 入站还原身份、出站向下游零解析透传四头。

## 跨语言四头契约（gRPC Metadata）

常量定义在 `grpc.constants.GrpcHeaders`，与 HTTP 头逐字一致：

| Metadata key | `GrpcHeaders` | 来源 |
|---|---|---|
| `X-User-Id` | `USER_ID_HEADER` | 网关 / 上游 |
| `X-User-Info` | `USER_INFO_HEADER` | 网关 / 上游 |
| `Authorization` | `AUTH_TOKEN_HEADER` | 客户端 → 网关透传 |
| `X-Client-Id` | `CLIENT_ID_HEADER` | 客户端拦截器写入 |

## 服务端拦截器 `MetadataInterceptor`

自动注册（`@GlobalInterceptor`），**无需任何配置**：

1. 读入站 Metadata 四头 → 还原 `LoginUser` → 注入 `UserContext`；
2. 把 `Authorization` / `X-Client-Id` 暂存到 gRPC `Context`，供客户端拦截器向下游透传；
3. 请求结束（`onComplete` / `onCancel`）清空 `UserContext`。

业务侧直接取用户：

```java
import com.bigbear.quarkus.core.context.UserContext;
import com.bigbear.quarkus.core.model.LoginUser;

public class GreeterImpl extends GreeterGrpc.GreeterImplBase {
    @Override
    public void hello(HelloRequest req, StreamObserver<HelloReply> obs) {
        LoginUser u = UserContext.current(); // gRPC 入站已由拦截器注入
        String uid = u != null ? u.getUserId() : "anonymous";
        // ...
    }
}
```

## 客户端拦截器 `AuthClientInterceptor`

自动注册（`@GlobalInterceptor`）。从 `UserContext`（由同一进程内服务端拦截器 / REST 过滤器注入）取当前用户，连同暂存的 `Authorization` / `X-Client-Id` 写入出站 Metadata，向下游透传。

调用方只需正常注入 stub（Quarkus dev 服务 / 配置客户端即可）：

```java
@Inject
@GrpcClient("greeter")
Greeter greeter;

public void call() {
    // UserContext 在当前请求上下文中已有值（来自入站 REST/gRPC）
    greeter.hello(HelloRequest.newBuilder().setName("x").build());
    // 出站时 AuthClientInterceptor 自动把身份写入 Metadata
}
```

## gRPC 客户端配置示例（`application.properties`）

```properties
# 服务发现：本地可走 Nacos（自定义 scheme），生产走 K8s dns:///
quarkus.grpc.clients.greeter.host=dns:///grpc-greeter-service
quarkus.grpc.clients.greeter.load-balancing-policy=round_robin
```

## 匿名放行

入站 Metadata 无 `X-User-Id` 时，`MetadataInterceptor` 注入 `null`，业务侧 `UserContext.isAnonymous()` 返回 `true`，对应网关白名单接口。
