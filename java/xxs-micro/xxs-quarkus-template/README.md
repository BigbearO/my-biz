# xxs-quarkus-template —— Quarkus 生产落地模板

基于 **Quarkus 3.33.2.1** 的可运行生产模板，演示：

- **Nacos 服务注册 / 发现**（自研 `xxs-quarkus-nacos-discovery` Stork 适配器，`type=nacos`）
- **官方配置中心**（`io.quarkiverse.config:quarkus-config-nacos`，HTTP 直连 Nacos）
- **gRPC 双注册 + 跨服务发现调用**（provider 暴露 gRPC，consumer 经 Stork + Nacos `round_robin` 调用）
- **容器化**（JVM 镜像 + docker-compose 一键联调）

---

## 1. 模块结构

```
xxs-quarkus-template/
├── template-api/          # gRPC 契约单一源：hello.proto（provider/consumer 复制使用）
├── template-provider/     # 服务提供者：gRPC 服务端 + HTTP/gRPC 双注册 + 配置中心
├── template-consumer/     # 服务消费者：REST 入口 + gRPC 客户端（stork 发现）+ 自身 HTTP 注册
├── docker-compose.yml     # Nacos standalone + provider + consumer 一键联调
└── README.md
```

> **关于 proto 单一源**：Quarkus gRPC 要求每个使用方在自己模块的 `src/main/proto` 编译生成 stub，
> 因此 `hello.proto` 在 provider/consumer 各保留一份副本（与 `template-api` 内容一致）。
> 生产环境可使用 `maven-dependency-plugin` 在构建期从 `template-api` 解包 proto 实现真正单一源。

## 2. 技术栈与版本

| 组件 | 版本 / 说明 |
|---|---|
| Quarkus 平台 | 3.33.2.1（由 `xxs-common-quarkus/pom.xml` 的 `quarkus.platform.version` 统一控制） |
| quarkus-config-nacos | 2.5.0（官方配置中心扩展，HTTP 直连，不依赖 nacos-client SDK） |
| nacos-client | 2.5.1（自研 Stork 适配器独立引入，版本由 `xxs-dependencies` 登记） |
| Stork | 2.7.9（随 Quarkus 平台） |
| Nacos Server | 2.5.1（docker-compose 镜像） |

## 3. 快速开始（docker-compose 联调）

```bash
# 1) 构建整个 reactor（含父模块与 Nacos 适配器），产物为各模块的 target/quarkus-app
mvn -pl xxs-quarkus-template -am package -DskipTests

# 2) 一键启动 Nacos + provider + consumer
cd xxs-quarkus-template
docker compose up --build
```

> 若本机未安装 Docker，可直接用开发模式分别启动（见第 4 节）。

### 验证

```bash
# consumer 通过 Nacos 发现 provider-grpc 并调用
curl "http://localhost:8081/hello?name=quarkus"
# 期望返回： Hello quarkus from template-provider

# 健康检查
curl http://localhost:8080/q/health/live
curl http://localhost:8081/q/health/live

# 查看 Nacos 注册实例（控制台 http://localhost:8848/nacos 默认 nacos/nacos）
```

停掉一个 provider 实例后，Nacos 客户端心跳注销，consumer 端 Stork 会在 `refresh-period`（默认 5M）或订阅变更后剔除该实例。

## 4. 本地开发模式

分别打开两个终端（均需先 `mvn -pl xxs-common-quarkus -am install` 将父模块与适配器装入本地仓库）：

```bash
# 终端 A：provider
mvn -pl xxs-quarkus-template/template-provider quarkus:dev

# 终端 B：consumer
mvn -pl xxs-quarkus-template/template-consumer quarkus:dev
```

开发模式默认会触发 `quarkus-config-nacos` 的 devservices 拉起 Nacos；如需复用 docker-compose 的 Nacos，
可在 `application.properties` 中保持 `quarkus.config.source.nacos.devservices.enabled=false` 并指向 `127.0.0.1:8848`。

## 5. 配置中心（单 data-id）

官方 `quarkus-config-nacos` 仅支持**单 data-id**，本模板约定：

- 每服务一个 data-id：`quarkus.config.source.nacos.data-id=${quarkus.application.name}.properties`
  （如 `template-provider.properties`、`template-consumer.properties`）
- 连接信息：`quarkus.config.source.nacos.server-addr / namespace / group`

> **连接信息共用**：自研 Stork 适配器的发现/注册连接参数**默认读取**
> `quarkus.config.source.nacos.*`，因此 Nacos 地址只需在配置中心扩展处配置一处，
> 注册与发现自动共用，无需重复声明 `stork.<svc>.service-discovery.server-addr`。
> 仅在需要按服务覆盖时才在 Stork 配置下写对应属性。

## 6. 服务注册 / 发现原理

### 双注册（provider 启动时自动完成）

| Stork 服务名 | 协议 | 端口来源 | 说明 |
|---|---|---|---|
| `template-provider` | http | `quarkus.http.port` | HTTP 服务注册 |
| `template-provider-grpc` | grpc | `quarkus.grpc.server.port` | gRPC 服务注册，适配器按 `-grpc` 后缀写入 `protocol=grpc` 元数据 |

### 发现 + 调用（consumer）

```
GET /hello
  └─> GreeterResource
        └─> @GrpcClient("greeter") Greeter
              host = stork://template-provider-grpc
              └─> Stork discovery.type=nacos
                    └─> Nacos 查询 template-provider-grpc 健康实例
                          └─> round_robin 选一个 → gRPC 调用 provider
```

服务实例在 Nacos 不可用（或网络抖动）时不影响应用启动，Stork 会复用上次结果并在 `refresh-period` 周期或订阅变更后刷新。

## 7. Kubernetes 部署要点

容器中 **不能** 以 `localhost` 注册（否则其它 Pod 无法访问）。通过 Downward API 将 Pod IP 注入 `APP_HOST`：

```yaml
env:
  - name: APP_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.podIP
```

对应 `application.properties` 中 `quarkus.stork.<svc>.service-registrar.parameters.host=${app.host:localhost}` 即会使用 Pod IP 注册。
若要求 Nacos 主动探活（而非客户端心跳），将注册配置 `ephemeral=false` 并在实例元数据携带 `healthUrl=/q/health/live`。

## 8. 已知限制与后续

| 项 | 说明 |
|---|---|
| 多 data-id | 官方 `quarkus-config-nacos` 原生仅单 data-id，本期不改源码；共享配置可放 `application.properties` 或复制进各 data-id |
| quarkus-config-nacos 兼容性 | 扩展 2.5.0 基于 Quarkus 3.31 构建，与 3.33.2.1 通常为小版本兼容；如遇扩展不兼容，升级到匹配 3.33 的更高 `quarkus-config-extensions` 版本即可 |
| Native 镜像 | 本期 JVM 优先；nacos-client（Netty）的 `reflect-config` / `resource-config` 留作后续适配 |
| proto 单一源 | 见第 1 节说明，生产可用插件同步 |
```
