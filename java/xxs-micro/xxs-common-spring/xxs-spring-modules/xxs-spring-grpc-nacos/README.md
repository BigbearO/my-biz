# xxs-spring-grpc-nacos — Spring gRPC + Nacos 服务发现/注册

基于 **Spring Boot 4.1 官方原生 gRPC（`org.springframework.grpc`）** 补齐 Nacos 服务发现与注册能力：
官方 starter 仅内置 `static://` / `dns://`，本模块扩展 `nacos://` scheme，实现从 Nacos 发现 gRPC 实例，
并把 gRPC 端口以 `<appName>-grpc` 服务名注册到 Nacos（与 HTTP 端口分属两个服务，互不干扰）。

与 `xxs-spring-grpc`（鉴权透传）、`greeter-api`（契约 SDK）配合形成完整链路。

## 能力一览

| 能力 | 说明 |
| --- | --- |
| `nacos://` NameResolver | 客户端按 `target: nacos://<serviceName>` 从 Nacos 拉取实例，支持健康过滤与订阅热刷新 |
| gRPC 端口注册 | 应用启动后以 `<spring.application.name>-grpc` 注册 gRPC 端口，关闭前注销（临时实例） |
| 默认 round_robin | 全局为所有 gRPC Channel 设置 `round_robin` 负载均衡 |
| 零侵入 | 仅依赖 `grpc.nacos.enabled=true` 开关，与官方 `@GrpcService` / `@GrpcClient` 无缝协作 |

## Maven 引入

```xml
<dependency>
  <groupId>com.bigbear</groupId>
  <artifactId>xxs-spring-grpc-nacos</artifactId>
</dependency>
```

> gRPC 版本由 `spring-boot-dependencies(4.1.0)` 管理；Nacos 版本由 `spring-cloud-alibaba-dependencies` 管理。

## 服务端：注册 gRPC 端口

```yaml
spring:
  application:
    name: greeter
  grpc:
    server:
      port: 9090          # gRPC 端口
grpc:
  nacos:
    enabled: true
    server-addr: 127.0.0.1:8848
    namespace: dev
    server-registration: true   # 默认 true：注册 greeter-grpc:9090
```

若还需把 HTTP 端口（如 8080）以 `greeter` 注册到 Nacos，额外引入
`spring-cloud-starter-alibaba-nacos-discovery` 并配置 `spring.cloud.nacos.discovery.*` 即可（双端口双注册）。

## 客户端：发现 gRPC 实例

```yaml
spring:
  grpc:
    client:
      channel:
        greeter:
          target: nacos://greeter-grpc   # scheme=nacos，host=Nacos 服务名
          negotiation-type: plaintext    # 开发期无 TLS；生产改用 ssl.bundle
grpc:
  nacos:
    enabled: true
    server-addr: 127.0.0.1:8848
    namespace: dev
```

声明通道后，业务侧两种方式使用：

```java
// 方式 A：直接注入 Stub
@ImportGrpcClients(target = "greeter", types = GreeterGrpc.GreeterBlockingStub.class)
public class GrpcClientConfig {}

// 方式 B：引入 greeter-api，使用充血 GreeterClient（推荐）
GreeterClient client = ...;
String msg = client.sayHello("world");
```

## 配置项（`grpc.nacos.*`）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 总开关，必须显式 `true` 才启用发现/注册 |
| `server-addr` | — | Nacos 地址，如 `127.0.0.1:8848` |
| `namespace` | `""` | 命名空间（环境隔离） |
| `group` | `DEFAULT_GROUP` | 服务分组 |
| `cluster` | `DEFAULT` | 集群名 |
| `username` / `password` | — | 鉴权凭据（开启时必填） |
| `healthy-only` | `true` | 仅返回健康实例 |
| `subscribe` | `true` | 订阅实例变更实现热刷新 |
| `server-registration` | `true` | 是否注册 gRPC 端口 |
| `service-suffix` | `-grpc` | Nacos gRPC 服务名后缀（拼接在 `spring.application.name` 之后） |

## 跨语言：Go / kratos

proto 单一真源位于仓库 `proto/`（与 Java 同仓），`greeter/v1/greeter.proto` 同时声明 `java_package` 与 `go_package`。

Go 侧用 Buf 生成 stub（在项目内指向共享 proto）：

```bash
cd go/your-svc
buf generate --path ../../proto
```

kratos 客户端 endpoint 复用同一 Nacos 服务名约定（`-grpc` 后缀），无需改业务代码：

```yaml
greeter:
  endpoint: nacos://greeter-grpc      # 与 Java 共享 Nacos 命名空间与服务名
  # 负载均衡 round_robin + 明文（与 Java 客户端语义一致）
```

> Go 侧需自备 `nacos://` resolver（kratos `contrib/registry/nacos` 提供），确保 `namespace` 与 `group` 与 Java 侧一致。

## 设计要点

- **编程式注册 NameResolver**：gRPC 的 SPI 机制无法向 `NameResolverProvider` 注入 Spring Bean（NamingService / 配置），
  故在 `GrpcNacosClientAutoConfiguration` 中以 `NameResolverRegistry.getDefaultRegistry().register(provider)` 编程式注册。
- **无需 metadata 探测端口**：gRPC 与 HTTP 分属不同 Nacos 服务名（`-grpc` 后缀），NameResolver 直接取 `instance.getPort()`。
- **临时实例**：注册使用 `ephemeral=true`，进程异常退出时由 Nacos 自动剔除，避免残留无效地址。
- **热刷新**：`subscribe=true` 时监听 Nacos `NamingEvent`，实例上下线即时反映到 gRPC Channel。
