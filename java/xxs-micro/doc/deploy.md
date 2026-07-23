# gRPC 地址切换 K8s 必改配置清单

> 目标：上线后将 gRPC 地址由 Nacos（`nacos://` / `stork+nacos`）切换为 K8s Service（`dns:///`），
> **仅改配置、不动业务代码**。`<命名空间>` 替换为实际 K8s 命名空间。
> 服务端端口（Spring `9090` / Quarkus `9090`）保持不变，作为 K8s Service 的 `targetPort`。

## 一、Spring 项目 `xxs-spring-grpc-demo`

配置文件：`xxs-common-spring/xxs-spring-modules/xxs-spring-grpc-demo/src/main/resources/application.yml`

| 配置项 | 改前 | 改后 | 说明 |
| --- | --- | --- | --- |
| `spring.grpc.client.channel.greeter.target` | `nacos://greeter-grpc` | `dns:///greeter.<命名空间>.svc.cluster.local:9090` | greeter gRPC 通道改走 K8s DNS 解析 |
| `spring.grpc.client.channel.user.target` | `nacos://bear-ai.grpc` | `dns:///bear-ai-grpc.<命名空间>.svc.cluster.local:9090` | user gRPC 通道改走 K8s DNS 解析 |
| `grpc.nacos.enabled` | `true` | `false` | 关闭 Nacos 解析器注册与服务端口注册，K8s 内不再依赖 Nacos |

## 二、Quarkus 项目 `xxs-quarkus-template` — consumer

配置文件：`xxs-quarkus-template/template-consumer/src/main/resources/application.properties`

| 配置项 | 改前 | 改后 | 说明 |
| --- | --- | --- | --- |
| `quarkus.grpc.clients.greeter.name-resolver` | `stork` | `dns` | 改用 gRPC 原生 DNS 解析，不再经 Stork+Nacos |
| `quarkus.grpc.clients.greeter.host` | `template-provider-grpc` | `dns:///template-provider-grpc.<命名空间>.svc.cluster.local` | 指向 K8s Service（Headless 则返回各 Pod IP） |
| `quarkus.grpc.clients.greeter.load-balancing-policy` | （无） | `round_robin` | 客户端轮询负载均衡（纯配置支持） |

## 三、Quarkus 项目 `xxs-quarkus-template` — provider

配置文件：`xxs-quarkus-template/template-provider/src/main/resources/application.properties`

| 配置项 | 改前 | 改后 | 说明 |
| --- | --- | --- | --- |
| `quarkus.stork.template-provider.service-registrar.type` | `nacos` | 清空 / 注释 | 不再将 HTTP 服务注册到 Nacos |
| `quarkus.stork.template-provider-grpc.service-registrar.type` | `nacos` | 清空 / 注释 | 不再将 gRPC 服务注册到 Nacos |

> provider 端 `quarkus.grpc.server.port=9090` 保持不变，作为 K8s Service 的 `targetPort`。

## 四、配套说明

- **业务代码零改动**：`GreeterController` / `UserController` / `GreeterService`（Spring）、`GreeterResource`（Quarkus）仅引用 channel 名，不涉及具体地址。
- **依赖零改动**：`dns:///` 为 gRPC / Quarkus 原生能力，无需新增依赖。
- **负载均衡方式**：推荐使用 **ClusterIP Service + kube-proxy 连接层均衡**（Spring / Quarkus 均适用，最简单）；Quarkus 已配 `round_robin`，Headless Service 亦可纯配置生效。Spring 若用 Headless + 客户端 `round_robin`，因官方 `spring-grpc` 未暴露 LB 策略配置项，需补一个 tiny bean（非纯配置）。
- **K8s 侧必需资源**：新建对应 `Service`（ClusterIP 或 Headless，`port:9090 → targetPort:9090`），名称需与上方 `dns:///<svc>` 一致。
