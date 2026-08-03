# Seata 分布式事务接入方案（Spring gRPC → Go + Quarkus，XA 模式，PostgreSQL 16）

> 目标：在 `java/xxs-micro/xxs-common-spring/xxs-spring-modules/xxs-spring-grpc-demo`
> 通过 gRPC 调用 `go/bear-admin` 与 `java/xxs-micro/xxs-quarkus-template/template-provider` 时，
> 使用 Seata **XA 模式** 串起跨语言分布式事务。
>
> - 数据库统一为 **PostgreSQL 16**（非 MySQL）。三端业务库可位于同一 PG 实例的不同 database。
> - Spring 侧：**方案 B（RuoYi 动态数据源 + Seata）**，复用 `ruoyi-common-seata`，作全局事务**发起方**。
> - Go 侧：`seata-go` XA 驱动（`SeataXAPostgresDriver`，基于 pgx），入站绑定 XID，作 JOIN **分支**。
> - Quarkus 侧：因 Seata 无官方 Quarkus 扩展，用 `seata-core` 手动 CDI 装配，作 JOIN **并列分支**。
> - 三端事务模式统一为 **XA**，无需 `undo_log` 表。
>
> ⚠️ **PostgreSQL 启用 XA 的硬性前提**：实例 `postgresql.conf` 必须设置
> `max_prepared_transactions`（默认 `0` = 禁用预备事务），否则 XA 的
> `PREPARE TRANSACTION` 两阶段提交会直接报错。建议设为 `200` 且 `>= max_connections` 量级，
> 修改后需重启 PG 实例生效；各端连接用户需具备 `PREPARE TRANSACTION` 权限。

---

## 〇、最终事务拓扑

```
                              Spring(xxs-spring-grpc-demo)
                              @GlobalTransactional  开启全局事务, XID = "xxx"
                              RootContext.getXID() = XID
                                  │
        ┌─────────────────────────┼─────────────────────────────────┐
        │  gRPC 出站拦截器: Metadata[TX_XID] = XID                    │
        ▼ (user 通道)                                            ▼ (quarkus 通道, 新增)
   Go(bear-admin)                                      Quarkus(template-provider)
   gRPC 入站中间件: tm.SetXID(ctx, xid)  JOIN           gRPC 入站拦截器: RootContext.bind(xid)  JOIN
        │                                                     │
        ▼                                                     ▼
   本地写 user_tbl(XA 分支)                              本地写 biz_tbl(XA 分支)

   Spring 本地写 biz_tbl(XA 分支, dynamic-datasource 代理)

   分支全部就绪 → TC 统一提交 / 任意异常 → 统一回滚(三库均回滚)
```

**跨语言契约（必须三端一致）：**

| 项 | 值 | 说明 |
|----|----|----|
| XID 元数据键 | `TX_XID` | Java `RootContext.KEY_XID` == Go `constant.XidKey` == Quarkus `RootContext.KEY_XID` |
| 事务模式 | `XA` | 三端均用 XA，无需 `undo_log` 表 |
| 事务分组映射 | `default_tx_group: default` | 三端 vgroup-mapping 指向同一 cluster |
| 注册/配置中心 | Nacos `127.0.0.1:8848` / namespace `xhf` | 与现有 gRPC 注册共用 |
| application-id | 各端 `spring.application.name` / quarkus `quarkus.application.name` | 用于 TC 区分客户端 |

---

## 一、事务传播模型（核心认知）

Seata 的跨服务事务本质 **不是** 数据库联动，而是 **XID（全局事务号）随 RPC 链路透传**：

1. **发起方**：`@GlobalTransactional` 开启全局事务，TC 下发 XID，写入 `RootContext`（ThreadLocal）。
2. **出站透传**：gRPC 客户端拦截器把 `RootContext.getXID()` 写入 `Metadata[TX_XID]`。
3. **入站 JOIN**：下游 gRPC 服务端拦截器从 `Metadata` 取出 XID，**绑定**到本地上下文
   （Java/Quarkus 用 `RootContext.bind`，Go 用 `tm.SetXID`），切勿再开新全局事务。
4. **分支注册**：下游用 XA 代理数据源执行 DB 写，自动向 TC 注册为 XA 分支。
5. **二阶段**：发起方方法正常返回 → TC 通知各分支 `commit`；抛异常 → TC 通知各分支 `rollback`。

> 关键：**只 JOIN 不新开**。下游拿到 XID 后绑定即可，严禁再调 `tm.WithGlobalTx` / `@GlobalTransactional`。

---

## 二、前置准备

### 2.1 Seata Server（TC）

- 使用官方 `seata-server`，注册中心与配置中心指向同一 Nacos（`namespace=xhf`）。
- **存储库用 PostgreSQL**：`store.db.dbType=postgresql`，并采用 Seata 官方 `sql/postgresql.sql`
  （全局表 / 分支表 / 锁表）建库建表，**不要手搓 MySQL 版 `seata.sql`**（PG 不支持 `AUTO_INCREMENT`、
  反引号、`ENGINE` 等）。
- 在 Nacos `xhf` 命名空间下创建 `seata-server.properties` 配置（核心项）：
  ```properties
  store.mode=db
  store.db.dbType=postgresql
  store.db.driverClassName=org.postgresql.Driver
  store.db.url=jdbc:postgresql://192.168.50.45:5432/seata?currentSchema=public
  store.db.user=root
  store.db.password=123456
  service.vgroupMapping.default_tx_group=default
  ```
- TC 监听 `127.0.0.1:8091`（与下方各端 `seata.service.grouplist` 对应）。

### 2.2 数据库初始化 SQL

**TC 侧（`seata` 库）** —— 取 Seata 官方 `sql/postgresql.sql`（`global_table` / `branch_table` / `lock_table`），
在 PG 实例上创建 `seata` database 后执行。

**业务侧三库**（`seata_java` / `seata_go` / `seata_quarkus`）—— XA 模式**不需要** `undo_log`，只需普通业务表。
幂等建表脚本：`java/xxs-micro/sql/seata-xa-demo.sql`（`\connect` 分库执行）。
**注意**：XA 依赖 PG 预备事务，请先确认 `postgresql.conf` 中 `max_prepared_transactions > 0`（详见开头说明）。

三张业务表与三端 INSERT 严格对应（列不可随意改动）：

```sql
-- seata_java.biz_tbl（Spring 发起方本地分支；demo 写 name + amount，amount numeric）
CREATE TABLE IF NOT EXISTS biz_tbl (
  id     SERIAL PRIMARY KEY,
  name   VARCHAR(128) NOT NULL,
  amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
  ts     TIMESTAMP NOT NULL DEFAULT now()
);

-- seata_go.user_tbl（Go 分支；demo 经 CreateUser RPC 调用，仅写 name）
CREATE TABLE IF NOT EXISTS user_tbl (
  id   SERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  ts   TIMESTAMP NOT NULL DEFAULT now()
);

-- seata_quarkus.biz_tbl（Quarkus 并列分支；demo 经 CreateBiz RPC 写入 name + val，val int4）
CREATE TABLE IF NOT EXISTS biz_tbl (
  id   BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  val  INTEGER NOT NULL,
  ts   TIMESTAMP NOT NULL DEFAULT now()
);
```

> **列对齐提醒**：`seata_java.biz_tbl` 与 `seata_quarkus.biz_tbl` 同名不同列（前者 `amount numeric`、后者 `val int`），
> 属两个独立库的独立表，勿混用；改表结构时需同步改三端 INSERT。

---

## 三、Spring 侧改造（xxs-spring-grpc-demo，方案 B + 三段事务）

### 3.1 `pom.xml` 增加依赖

`ruoyi-common-seata` 已由 `xxs-spring-ruoyi-bom` 纳入 `dependencyManagement`，
因此**只需声明、无需填版本**；并引入 RuoYi 动态数据源与 PostgreSQL 驱动。
同时新增调用 Quarkus 的 `biz-api`（见第六章共享契约）。

`java/.../xxs-spring-grpc-demo/pom.xml`：

```xml
<dependencies>
    <!-- 现有依赖：xxs-spring-grpc / xxs-spring-grpc-nacos / greeter-api / user-api / web / nacos-discovery ... -->

    <!-- Seata 事务（复用 RuoYi，已纳入 BOM 版本管理） -->
    <dependency>
        <groupId>org.dromara</groupId>
        <artifactId>ruoyi-common-seata</artifactId>
    </dependency>

    <!-- RuoYi 动态数据源：提供数据源代理并对接 Seata（方案 B 关键） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>dynamic-datasource-spring-boot-starter</artifactId>
        <version>4.3.1</version>
    </dependency>

    <!-- PostgreSQL 驱动 -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>

    <!-- 新增：调用 Quarkus 的 BizService 的客户端 SDK（共享 proto，见第六章） -->
    <dependency>
        <groupId>com.bigbear.xxs</groupId>
        <artifactId>biz-api</artifactId>
    </dependency>
</dependencies>
```

> 说明：`ruoyi-common-seata` 内部已带 `seata-spring-boot-starter`（apache seata），不再额外引入。

### 3.2 `application.yml` 配置

替换/补充 `xxs-spring-grpc-demo/src/main/resources/application.yml`，
**关闭 Seata 自带代理，交由 dynamic-datasource 代理**（方案 B 与方案 A 的唯一差异点），
并新增 `quarkus` gRPC 通道（目标 `nacos://template-provider-grpc`）。

```yaml
server:
  port: 8088

spring:
  application:
    name: greeter
  # ===== 方案 B：RuoYi 动态数据源代理 Seata =====
  datasource:
    dynamic:
      seata: true                 # 交给 dynamic-datasource 做 XA 代理
      primary: master
      strict: false
      datasource:
        master:
          driver-class-name: org.postgresql.Driver
          url: jdbc:postgresql://192.168.50.45:5432/seata_java?currentSchema=public
          username: root
          password: 123456
  grpc:
    server:
      port: 9099
    client:
      channel:
        greeter:
          target: nacos://greeter-grpc
        user:                               # → Go bear-admin
          target: nacos://bear-ai.grpc
        quarkus:                             # 新增：→ Quarkus template-provider
          target: nacos://template-provider-grpc
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848
      username: nacos
      password: nacos
      discovery:
        group: DEFAULT_GROUP
        server-addr: 127.0.0.1:8848
        namespace: xhf

grpc:
  nacos:
    enabled: true
    server-addr: 127.0.0.1:8848
    namespace: xhf
    server-registration: true
    username: nacos
    password: nacos
    group: DEFAULT_GROUP

# ===== Seata 客户端（XA 模式） =====
seata:
  enabled: true
  enable-auto-data-source-proxy: false   # 关键：关闭自带代理，避免与 dynamic-datasource 重复代理
  application-id: ${spring.application.name}
  tx-service-group: default_tx_group
  data-source-proxy-mode: XA
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      group: DEFAULT_GROUP
      namespace: xhf
      application: seata-server
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      group: DEFAULT_GROUP
      namespace: xhf
      data-id: seata-server.properties
  service:
    vgroup-mapping:
      default_tx_group: default
    grouplist:
      default: 127.0.0.1:8091
```

### 3.3 新增 `SeataXidClientInterceptor`（gRPC 出站透传 XID）

在 grpc-demo 模块内新增拦截器，把当前线程的 XID 写入 gRPC Metadata `TX_XID` 头。

`src/main/java/com/bigbear/spring/grpc/demo/seata/SeataXidClientInterceptor.java`：

```java
package com.bigbear.spring.grpc.demo.seata;

import org.apache.seata.core.context.RootContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * gRPC 客户端拦截器：全局事务 XID 透传（Seata → Go / Quarkus 分支）。
 */
public class SeataXidClientInterceptor implements ClientInterceptor {

    /** 与 Go / Quarkus 入站侧约定的 XID 透传头。 */
    public static final Metadata.Key<String> XID_KEY =
        Metadata.Key.of("TX_XID", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String xid = RootContext.getXID();
                if (xid != null) {
                    headers.put(XID_KEY, xid);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
```

### 3.4 以全局拦截器挂载（覆盖 `user` 与 `quarkus` 两个通道）

demo 通过 Spring gRPC 官方 `@GlobalClientInterceptor` 把拦截器注册为**全局客户端拦截器**，
自动应用到 demo 的所有 gRPC 通道（含 `user` →Go、`quarkus` →Quarkus），无需在每个通道的
`@ImportGrpcClients` 上单独挂载 `intercept=`。

`src/main/java/com/bigbear/spring/grpc/demo/seata/SeataXidGrpcConfiguration.java`：

```java
@Configuration(proxyBeanMethods = false)
public class SeataXidGrpcConfiguration {

    /** 注册 gRPC 客户端全局拦截器：出站自动携带当前全局事务 XID。 */
    @Bean
    @Order(200)
    @GlobalClientInterceptor
    public ClientInterceptor seataXidClientInterceptor() {
        return new SeataXidClientInterceptor();
    }
}
```

### 3.5 事务编排服务与回滚演练（`SeataDemoService`）

demo 的编排服务为 `seata/SeataDemoService`：`@GlobalTransactional` 开启全局事务，
`@DS("seata_java")` 路由本地写库，再分别经 `userClient`（→Go）、`bizClient`（→Quarkus）跨端写库；
另提供 `createUserThenFail` —— 三端落库后主动抛错，用于回滚演练。

`src/main/java/com/bigbear/spring/grpc/demo/seata/SeataDemoService.java`（核心节选）：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SeataDemoService {

    /** → Go 分支（seata-go XA）。 */
    private final UserClient userClient;

    /** → Quarkus 分支（seata-quarkus XA）。 */
    private final BizClient bizClient;

    /** 多数据源模板：路由到 seata_java 数据源。 */
    private final JdbcTemplate jdbcTemplate;

    @GlobalTransactional
    @DS("seata_java")
    public String createUser(String name, int age) {
        return doCreateUser(name, age);
    }

    /** 三端落库后主动抛错，验证 Seata XA 整单回滚。 */
    @GlobalTransactional
    @DS("seata_java")
    public String createUserThenFail(String name, int age) {
        String goId = doCreateUser(name, age);
        throw new RuntimeException("rollback drill after 3-branch write");
    }

    /** 三端落库核心：Java(seata_java.biz_tbl) → Go(seata_go.user_tbl) → Quarkus(seata_quarkus.biz_tbl)。 */
    private String doCreateUser(String name, int age) {
        // 1) Java 分支
        jdbcTemplate.update("INSERT INTO biz_tbl(name, amount) VALUES (?, ?)", name, 0);
        // 2) Go 分支：gRPC 透传 XID 调用 seata-go
        long goId = userClient.createUser(name, age).getId();
        // 3) Quarkus 分支：gRPC 透传 XID 调用 template-provider
        bizClient.createBiz(name, age);
        return String.valueOf(goId);
    }
}
```

`SeataController` 暴露两个 REST 入口：

- `POST /demo/seata/create-user?name=&age=` —— 三端正常落库；
- `POST /demo/seata/create-user-fail?name=&age=` —— 三端落库后主动抛错，验证整单回滚（三表均无残留）。

---
## 四、Go 侧改造（seata-go XA）

> **修正点（相对初稿）：** ① 配置须包在 `seata:` 根键下；② 模块路径以实际 `go get` 为准；
> ③ 优先复用 seata-go 官方 gRPC XID 拦截器，手写 `tm.SetXID` 仅作回退；
> ④ **seata-go v2.1.0 的 nacos registry/config 中心是 TODO 存根**，Go 侧必须用 `file` 注册中心
> （`service.vgroup-mapping` + `grouplist` 直连 TC），与 Spring 侧 `seata.service.grouplist` 对齐；
> ⑤ 项目实际配置目录是 `configs/`（非初稿的 `conf/`），对应 `configs/seatago.yml`。

### 4.1 `go.mod` 增加依赖

```go
require (
    seata.apache.org/seata-go/v2 v2.1.0          // goproxy.cn 可拉取的最新版（已含 pgx 适配）
)
```

执行（路径二选一，以实际能拉取的为准）：

```bash
cd go/bear-admin
go get seata.apache.org/seata-go/v2@v2.1.0      # 官方 vanity import
# 若上者不可用，回退：go get github.com/apache/incubator-seata-go/v2
```

> seata-go v2.1.0 的 PostgreSQL XA 基于 `pgx`，**无需** 再引入 `github.com/go-sql-driver/mysql`；
> 若该模块此前仅为此处引入 MySQL，可在 `go.mod` 中移除。
> 若 `go.mod` 中 `mysql` 还被其它模块引用则保留，仅本 XA 分支不再需要。

> seata-go v2.1.0 自身 go.mod 声明了 `replace github.com/dubbogo/gost => v1.13.2` 与
> `exclude polaris-go v1.3.0-alpha`；这些指令不会传递到主模块，需在 `bear-admin/go.mod` 中同步补上（已加）。

### 4.2 新增 `configs/seatago.yml`（`seata:` 根键 + file 注册中心）

与 Java 侧**共享同一 vgroup 映射**（`default_tx_group → default`）。注意顶层必须有 `seata:` 包裹（对齐官方 quickstart）。
由于 v2.1.0 nacos registry 未实现，**registry.type 用 `file`**，靠 `service.vgroup-mapping`+`grouplist` 直连 TC：

```yaml
seata:
  service:
    vgroup-mapping:
      default_tx_group: default
    grouplist:
      default: 127.0.0.1:8091
    enable-degrade: false
    disable-global-transaction: false
  client:
    tm:
      commit-retry-count: 5
      rollback-retry-count: 5
    rm:
      async-commit-buffer-limit: 10000
    lock:
      retry-interval: 30s
      retry-times: 10
  registry:
    type: file          # v2.1.0 nacos registry 为存根，file 直连 TC
    file:
      name: seatago.yml
  transport:
    shutdown:
      wait: 3s
    type: TCP
    server: NIO
    heartbeat: true
    serialization: seata
    compressor: none
    enable-tm-client-batch-send-request: false
    enable-rm-client-batch-send-request: true
    rpc-rm-request-timeout: 30s
    rpc-tm-request-timeout: 30s
  getty:
    reconnect-interval: 0
    connection-num: 1
    load-balance-type: XID
    session:
      compress-encoding: false
      tcp-no-delay: true
      tcp-keep-alive: true
      keep-alive-period: 120s
      tcp-r-buf-size: 262144
      tcp-w-buf-size: 65536
      tcp-read-timeout: 1s
      tcp-write-timeout: 5s
      wait-timeout: 1s
      max-msg-len: 16498688
      session-name: bear-admin
      cron-period: 1s
  log:
    exception-rate: 100
```

在 `main.go` 启动早期调用初始化（放在 `bootstrap.RunApp` 之前）：

```go
import seataclient "seata.apache.org/seata-go/v2/pkg/client"

func runApp() error {
    // ... 构建 bootstrap.NewContext 之后
    seataclient.InitPath("configs/seatago.yml")   // 必须在 gRPC 服务启动前
    // ... 原有 kratos 启动逻辑
}
```

### 4.3 gRPC 入站 XID 绑定中间件（JOIN 全局事务）

seata-go 官方在 `pkg/integration/grpc` 提供 `ServerTransactionInterceptor`（服务端入站 bind XID），
但它基于原生 `grpc.UnaryServerInterceptor` + `google.golang.org/grpc/metadata`，**无法直接挂进 kratos
的 `middleware` 链**（kratos 用的是自己的 `metadata.FromServerContext`）。因此本项目在 kratos 中间件内
等效实现官方拦截器逻辑：`tm.InitSeataContext(ctx)` + `tm.SetXID(ctx, xid)`。
> 注意：`tm.SetXID` 是无返回值、原地修改 `ContextVariable` 的 API；必须先 `InitSeataContext` 初始化，
> 否则 SetXID 是空操作。

`app/ai/service/internal/middleware/seata.go`：

```go
package middleware

import (
    "context"

    "github.com/go-kratos/kratos/v2/metadata"
    "github.com/go-kratos/kratos/v2/middleware"

    "seata.apache.org/seata-go/v2/pkg/constant"
    "seata.apache.org/seata-go/v2/pkg/tm"
)

// GrpcServerMiddleware 将入站 gRPC 的 Seata XID 绑定到 ctx，JOIN 上游全局事务。
//
// kratos 传输层把元数据转为小写键，因此同时兼容 TX_XID / tx_xid（XidKey / XidKeyLowercase）。
func GrpcServerMiddleware() middleware.Middleware {
    return func(handler middleware.Handler) middleware.Handler {
        return func(ctx context.Context, req interface{}) (interface{}, error) {
            xid := extractXID(ctx)
            if xid != "" {
                ctx = tm.InitSeataContext(ctx)
                tm.SetXID(ctx, xid)
            }
            return handler(ctx, req)
        }
    }
}

// extractXID 从 kratos server metadata 中读取 TX_XID（兼容小写 tx_xid）。
func extractXID(ctx context.Context) string {
    md, ok := metadata.FromServerContext(ctx)
    if !ok {
        return ""
    }
    if xid := md.Get(constant.XidKey); xid != "" {
        return xid
    }
    return md.Get(constant.XidKeyLowercase)
}
```

> 实现时以 seata-go 源码 `pkg/constant` 实际导出符号为准；启动时加日志断言
> `constant.XidKey == "TX_XID"`，避免与 Java 端键名不一致导致断链。

### 4.4 注册中间件到 gRPC 服务

`app/ai/service/internal/server/grpc_server.go` 的 `NewGrpcMiddleware` 中追加：

```go
func NewGrpcMiddleware(ctx *bootstrap.Context) GrpcMiddlewares {
    var ms []middleware.Middleware
    ms = append(ms, logx.TracedLoggingServer(logx.TeeLogger(ctx.GetLogger())))
    ms = append(ms, auth.GrpcServerMiddleware())
    ms = append(ms, seata.GrpcServerMiddleware()) // 新增：XID 绑定
    return ms
}
```

### 4.5 数据层：XA 驱动 + ctx 携带 XID 执行

新增 data 层，使用 Seata XA 驱动打开连接，**所有写操作必须 `ExecContext(ctx, ...)`**
（ctx 携带 XID，seata-go 才会注册 XA 分支）。`seata-xa-postgres` 驱动由 `client.InitPath` 完成注册，
故 `sql.Open` 须在 seata 客户端初始化之后调用（main.go 已保证）。
注意：pgx 驱动占位符必须为 `$1, $2`（非 MySQL 的 `?`）。

`app/ai/service/internal/data/user_repo.go`：

```go
package data

import (
    "context"
    "database/sql"

    "github.com/go-kratos/kratos/v2/log"
    "github.com/tx7do/kratos-bootstrap/bootstrap"

    sql2 "seata.apache.org/seata-go/v2/pkg/datasource/sql"
)

// userTableDSN 指向 PostgreSQL 业务库（与 configs/data.yaml 同一实例/库）。
// 采用 PG 关键字式 DSN；若库已存在且 user_tbl 在其中，直接复用。
const userTableDSN = "host=192.168.50.45 port=5432 user=root password=123456 dbname=gwa sslmode=disable"

type UserRepo struct {
    db  *sql.DB
    log *log.Helper
}

func NewUserRepo(ctx *bootstrap.Context) *UserRepo {
    // 使用 Seata XA PostgreSQL 驱动（基于 pgx），DSN 指向业务库
    db, err := sql.Open(sql2.SeataXAPostgresDriver, userTableDSN)
    if err != nil {
        panic(err)
    }
    return &UserRepo{db: db, log: logx.NewTracedHelper(ctx.GetLogger(), "ai/repo")}
}

// CreateUser 在全局事务分支内写入 user_tbl。
//
// 必须透传 ctx（含 XID），否则不会纳入 Seata 管理。
// PG 占位符使用 $1/$2。
func (r *UserRepo) CreateUser(ctx context.Context, name string, age int) error {
    _, err := r.db.ExecContext(ctx,
        "INSERT INTO user_tbl(name, age) VALUES ($1, $2)", name, age)
    return err
}
```

> 若 `user_tbl` 与 `configs/data.yaml` 的 `dbname=gwa` 不在同一库，请把 `dbname` 改为实际库名
> （如 `seata_go`）。保持两处 DSN 指向同一 PG 实例即可，`SeataXAPostgresDriver` 自带 pgx，无需额外驱动依赖。

### 4.6 业务服务新增 `CreateUser` RPC

`user_service.go` 增加实现，并调用 `UserRepo`（`user.proto` 需新增 `CreateUser` RPC 与消息，见第六章）：

```go
func (s *UserService) CreateUser(ctx context.Context, req *userpb.CreateUserRequest) (*userpb.CreateUserReply, error) {
    if err := s.userRepo.CreateUser(ctx, req.GetName(), int(req.GetAge())); err != nil {
        return nil, err
    }
    return &userpb.CreateUserReply{Success: true}, nil
}
```

> 重新生成 proto stub（`cd api && buf generate`，需先安装 protoc-gen-go-http / go-errors / validate / redact 插件），
> 并在 `wire` 中将 `UserRepo` 注入 `UserService`（`data/providers/wire_set.go` 已加 `data.NewUserRepo`，
> `cmd/server/wire_gen.go` 已按 wire 输出格式手工更新，服务端可跑 `make wire` 再确认）。

---

## 五、Quarkus 侧改造（template-provider，新增并列分支）

> **核心挑战：** Seata **无官方 Quarkus 扩展**，`seata-spring-boot-starter` / `spring-cloud-starter-alibaba-seata`
> 均为 Spring 专属，Quarkus（CDI 体系）无法直接复用。方案：基于 `seata-core` 自研公共扩展
> `xxs-quarkus-seata`（runtime + deployment 双模块）—— 配置 SPI 桥接 + TM/RM 客户端初始化 +
> `db-kind=seata` 自定义驱动（`SeataXADriver` → `DataSourceProxyXA`）+ gRPC XID 透传拦截器。
>
> 落点：新建公共扩展 `xxs-quarkus-seata`（deployment 为 `xxs-quarkus-seata-deployment`），
> 再集成进 `xxs-quarkus-template/template-provider`。

### 5.1 Seata 依赖与版本管理

`xxs-quarkus-seata` runtime 模块直接在 `pom.xml` 声明
`org.apache.seata:seata-core / seata-tm / seata-rm / seata-rm-datasource / seata-discovery-nacos / seata-config-nacos`，
**版本固定 2.6.0**，与 Spring 侧 `ruoyi-common-seata` 携带的 apache seata 版本对齐（两端与 TC 同主版本才可互通）。

> 早期方案拟在 `xxs-quarkus-bom` 的 `dependencyManagement` 纳入 `seata-core` 并排除传递的 `spring-*`；
> 当前实现未走 BOM，由 runtime 模块内直接声明（见 5.2 pom 片段）。后续如新增模块依赖 seata，可再收口到 BOM。

### 5.2 新建公共扩展（runtime `xxs-quarkus-seata` + deployment `xxs-quarkus-seata-deployment`）

在 `java/xxs-micro/xxs-common-quarkus/xxs-quarkus-modules/` 下新增**两个子模块**（runtime + deployment，
均已在 `xxs-quarkus-modules/pom.xml` 聚合）：

- `xxs-quarkus-seata`（runtime）：seata 配置 SPI 桥接 + TM/RM 客户端初始化 + XA 数据源驱动 + gRPC XID 透传拦截器；
- `xxs-quarkus-seata-deployment`（deployment）：注册 `db-kind=seata` → `SeataXADriver` 映射与扩展功能声明。

`xxs-quarkus-modules/xxs-quarkus-seata/pom.xml`（核心依赖，seata 版本 2.6.0 与 Spring 侧对齐）：

```xml
<dependencies>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-grpc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-agroal</artifactId>
    </dependency>
    <!-- 排除 quarkus-flyway-postgresql：离线仓库缺失，且 generate-code 会解析，导致离线构建失败 -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
        <exclusions>
            <exclusion>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-flyway-postgresql</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-core</artifactId>
        <version>2.6.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-tm</artifactId>
        <version>2.6.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-rm</artifactId>
        <version>2.6.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-rm-datasource</artifactId>
        <version>2.6.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-discovery-nacos</artifactId>
        <version>2.6.0</version>
    </dependency>
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-config-nacos</artifactId>
        <version>2.6.0</version>
    </dependency>
</dependencies>
```

> 构建细节：`META-INF/quarkus-extension.properties` 需要资源过滤内插 `${revision}`（deployment-artifact）；
> runtime 用 `jandex-maven-plugin` 生成索引，使 Bean / 拦截器被 Quarkus Arc 与 gRPC 扫描到。

组件全部位于 `com.bigbear.quarkus.seata` 包下，按职责拆分如下。

#### 5.2.1 配置桥接 SPI（`config/`）—— 摆脱 `registry.conf`

**问题**：seata-core 的 `ConfigurationFactory` 默认从 classpath 读 `registry.conf`/`file.conf` 确定
`registry.type`/`config.type`，而 Quarkus 的 ClassLoader 资源隔离导致该文件**无法稳定解析**
（`NotSupportYetException: config type can not be null`）。

**方案**：不依赖文件，全部配置经 Quarkus MicroProfile Config（`application.properties` 的 `seata.*` 键）承载，
再通过 Seata 官方 SPI `ExtConfigurationProvider` 以 `java.lang.reflect.Proxy` 动态代理叠加到 `Configuration` 上：
seata 内部按裸 key（如 `registry.type`、`service.vgroupMapping.xxx`）读取时，先解析 `seata.<dataId>`
（兼容驼峰 / 中划线 / 下划线变体，支持 `Duration` 转换），命中则返回，未命中回退原实现与默认值。

```java
// config/SeataConfigProvider.java（核心）
public class SeataConfigProvider implements ExtConfigurationProvider {
    @Override
    public Configuration provide(Configuration originalConfiguration) {
        return (Configuration) Proxy.newProxyInstance(
                Configuration.class.getClassLoader(),
                new Class<?>[] {Configuration.class},
                new ConfigurationBridge(originalConfiguration));
    }
    // ConfigurationBridge.invoke：get* 方法命中 seata.<dataId> 时按返回类型转换并返回，否则走原实现
}
```

SPI 声明：`META-INF/services/org.apache.seata.config.ExtConfigurationProvider` →
`com.bigbear.quarkus.seata.config.SeataConfigProvider`。

所有 `seata.*` 键在 `config/SeataConfigConstants.java` 统一定义：`seata.enabled` / `seata.application-id` /
`seata.tx-service-group` / `seata.datasource.url|username|password` / gRPC 透传头 `TX_XID`。

#### 5.2.2 运行时引导（`runtime/SeataRuntime`）—— TM/RM 初始化

`@ApplicationScoped @Startup` 应用启动钩子：`seata.enabled=true` 时调用
`RmNettyRemotingClient.getInstance(appId, group).init()` 与 `TmNettyRemotingClient.getInstance(appId, group).init()`，
使客户端连上 TC；`seata.enabled=false` 时跳过（普通本地事务，不连 TC）。`@PreDestroy` 时 `destroy()` 释放资源。

> 先后关系：RM 客户端**必须先于** `SeataXADriver` 首次构造 `DataSourceProxyXA` 完成初始化（资源管理器注册）。
> 因驱动为请求期懒加载，天然满足。

#### 5.2.3 事务协调客户端（`runtime/SeataTransactionClient`）

以 TM 身份向 TC 发起 `GlobalBegin / GlobalCommit / GlobalRollback`（`TmNettyRemotingClient.sendSyncRequest`），
供 Quarkus 端作为**全局事务协调者**（如 rollback-drill 演练、事务兜底补偿）时使用；作为普通 JOIN 分支时无需调用。
所有方法受 `SeataRuntime.isEnabled()` 门控，未启用直接返回失败。

#### 5.2.4 XA 数据源驱动（`runtime/SeataXADriver`）—— `db-kind=seata`

`java.sql.Driver` 实现，静态块注册进 `DriverManager`；承接 URL 前缀 `jdbc:seata:`（实际形如
`jdbc:seata:jdbc:postgresql://...`），懒加载构造全局单例 `DataSourceProxyXA(buildRealDataSource())`。

```java
// runtime/SeataXADriver.java（核心）
public class SeataXADriver implements Driver {
    private static final String URL_PREFIX = "jdbc:seata:";
    private static volatile DataSourceProxyXA dataSourceProxy;

    static {
        DriverManager.registerDriver(new SeataXADriver());   // 供 DriverManager / Agroal 发现
    }

    @Override
    public Connection connect(String url, Properties info) {
        return acceptsURL(url) ? dataSourceProxy().getConnection() : null;
    }

    private static DataSourceProxyXA dataSourceProxy() {
        // 懒加载；真实源为 PGSimpleDataSource（seata.datasource.* 配置）。
        // 必须在 RM 客户端 init（SeataRuntime 启动钩子）之后首次构造——连接发生在请求期，天然满足。
        if (dataSourceProxy == null) {
            synchronized (SeataXADriver.class) {
                if (dataSourceProxy == null) {
                    dataSourceProxy = new DataSourceProxyXA(buildRealDataSource());
                }
            }
        }
        return dataSourceProxy;
    }
}
```

> **为什么不需要 `PGXADataSource`**：`DataSourceProxyXA` 内部经 Seata `XAUtils.createXAConnection`
> 从普通 `Connection` 包装真实 XA 资源，底层 `PGSimpleDataSource` 即可。
> 全局事务内（`RootContext` 已绑定 XID）返回的连接代理自动登记 XA 分支；全局事务外退化为普通直连。
> `seata.enabled=false` 时 `AbstractResourceManager.registerResource` 仅内存登记、不连 TC，驱动懒构造安全。

#### 5.2.5 XID 跨线程传播（`runtime/SeataXidContext`）

```java
public final class SeataXidContext {
    /** gRPC Context 中承载全局事务 XID 的键 */
    public static final Context.Key<String> XID = Context.key(SeataConfigConstants.TX_XID);

    public static String get() {
        return XID.get();
    }

    /** 以指定 XID 绑定 RootContext 执行动作，finally 中安全解绑，避免线程本地状态泄漏 */
    public static <T> T runWithXid(String xid, Supplier<T> action) {
        if (xid == null || xid.isBlank()) {
            return action.get();
        }
        RootContext.bind(xid);
        try {
            return action.get();
        } finally {
            RootContext.unbind();
        }
    }
}
```

#### 5.2.6 gRPC 拦截器（`runtime/SeataServerInterceptor` / `SeataClientInterceptor`）

均通过 `@GlobalInterceptor` 由 Quarkus 自动注册（无需额外配置）。

- **服务端**（`SeataServerInterceptor`）：从入站 Metadata `TX_XID` 头读 XID，写入 gRPC Context
  （`Contexts.interceptCall`）。**设计要点（reactive 安全）**：Quarkus gRPC 服务方法返回 `Uni`，业务在
  worker 线程执行，与拦截器所在 event-loop 线程不同；故拦截器**不**直接绑定 `RootContext`
  （ThreadLocal 跨线程无效且有并发竞态），仅放 gRPC Context，业务侧在执行 DB 写前用
  `SeataXidContext.runWithXid` 在当前线程重绑。
- **客户端**（`SeataClientInterceptor`）：出站时优先取 gRPC Context 的 XID，回退 `RootContext.getXID()`，
  写入 `TX_XID` 头，供下游（Go / Quarkus 分支）JOIN 同一全局事务。

#### 5.2.7 deployment 模块（`xxs-quarkus-seata-deployment`）

`SeataProcessor` 三个 BuildStep：

```java
@BuildStep FeatureBuildItem feature()                          // "seata"
@BuildStep JdbcDriverBuildItem registerJdbcDriver()            // "seata" → SeataXADriver
@BuildStep ServiceProviderBuildItem registerDriverService()    // java.sql.Driver SPI（native 镜像）
```

> 因本地仓库缺失 `quarkus-extension-processor`，deployment 的构建步骤**手写**：
> `src/main/resources/META-INF/quarkus-build-steps.list` 内容为
> `com.bigbear.quarkus.seata.deployment.SeataProcessor`。

runtime 侧扩展元数据（`META-INF/`）：`quarkus-extension.properties`
（`deployment-artifact=com.bigbear:xxs-quarkus-seata-deployment:${revision}`，需资源过滤内插）+
`quarkus-extension.yaml`（名称 / 描述 / 关键字）；另有两份 `META-INF/services/`：
`java.sql.Driver` 与 `org.apache.seata.config.ExtConfigurationProvider`。

### 5.3 集成进 `template-provider`

`template-provider/pom.xml` 增加扩展依赖：

```xml
<dependencies>
    <!-- ... 现有依赖 ... -->

    <dependency>
        <groupId>com.bigbear</groupId>
        <artifactId>xxs-quarkus-seata</artifactId>
    </dependency>
    <dependency>
        <groupId>com.bigbear</groupId>
        <artifactId>xxs-quarkus-seata-deployment</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-agroal</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
        <exclusions>
            <exclusion>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-flyway-postgresql</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

`src/main/resources/application.properties`（数据源 + seata 配置，事务分组与 Spring 侧对齐）：

```properties
# ===== 数据源：db-kind=seata 自定义驱动，连接经 SeataXADriver → DataSourceProxyXA 供给 =====
quarkus.datasource.db-kind=seata
quarkus.datasource.jdbc.url=jdbc:seata:jdbc:postgresql://192.168.50.45:5432/seata_quarkus?currentSchema=public
quarkus.datasource.username=root
quarkus.datasource.password=123456

# ===== Seata 客户端（对齐 Spring 侧：tx-service-group=greeter-group） =====
seata.enabled=${SEATA_ENABLED:false}
seata.application-id=template-provider
seata.tx-service-group=greeter-group
seata.service.vgroup-mapping.greeter-group=default
seata.service.grouplist.default=127.0.0.1:8091
# XA 数据源真实连接参数（SeataXADriver 内部 PGSimpleDataSource 使用）
seata.datasource.url=jdbc:postgresql://192.168.50.45:5432/seata_quarkus?currentSchema=public
seata.datasource.username=root
seata.datasource.password=123456
# 注册/配置中心（Nacos xhf 命名空间，经 SPI 桥接读取）
seata.registry.type=nacos
seata.registry.nacos.server-addr=127.0.0.1:8848
seata.registry.nacos.namespace=xhf
seata.registry.nacos.group=DEFAULT_GROUP
seata.registry.nacos.application=seata-server
seata.registry.nacos.username=nacos
seata.registry.nacos.password=nacos
seata.config.type=nacos
seata.config.nacos.server-addr=127.0.0.1:8848
seata.config.nacos.namespace=xhf
seata.config.nacos.group=DEFAULT_GROUP
seata.config.nacos.data-id=seata-server.properties
seata.config.nacos.username=nacos
seata.config.nacos.password=nacos
```

> 无需再放 `registry.conf`/`file.conf`（SPI 桥接已替代，见 5.2.1）。
> `quarkus.grpc.server.port=9090` 同时作为 `template-provider-grpc` 的注册端口，与 Spring 侧
> `spring.grpc.client.channel.quarkus.target=nacos://template-provider-grpc` 对应。

### 5.4 新增 `biz.proto` 与 `BizService` 实现

`template-provider/src/main/proto/biz.proto`（Quarkus 侧契约，`quarkus-maven-plugin` 的 `generate-code`
goal 自动生成服务端 stub）：wire 包名必须为 `com.bigbear.quarkus.template.grpc`，
否则与 Spring 客户端的方法路径不一致（详见 6.1）。

`src/main/java/com/bigbear/quarkus/template/provider/BizServiceImpl.java`：

```java
@Slf4j
@GrpcService
public class BizServiceImpl implements BizService {

    /** db-kind=seata 的 Agroal 连接池（连接经 SeataXADriver 供给，即 XA 代理连接）。 */
    private final DataSource dataSource;

    @Override
    public Uni<CreateBizReply> createBiz(CreateBizRequest request) {
        return Uni.createFrom().item(() -> SeataXidContext.runWithXid(SeataXidContext.get(), () -> {
            insert(request);
            return CreateBizReply.newBuilder().setSuccess(true).build();
        }));
    }

    /** 向本地 biz_tbl 写入；抛异常使 gRPC 调用失败 → 发起方全局事务整体回滚 */
    private void insert(CreateBizRequest request) {
        String sql = "INSERT INTO biz_tbl(name, val) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.getName());
            ps.setInt(2, request.getVal());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("createBiz failed: " + request.getName(), e);
        }
    }
}
```

> `runWithXid(SeataXidContext.get(), ...)`：入站拦截器已把 XID 放入 gRPC Context，此处取到后
> 在当前执行线程上临时绑定 `RootContext`，使 `ConnectionProxyXA` 识别全局事务并登记 XA 分支；
> 无 XID（非事务调用）退化为普通本地写。
> 注入的 `DataSource` 即 Agroal 池（`db-kind=seata`），无需自定义 `@SeataXA` 限定符，规避 CDI 歧义。

---
## 六、共享契约（proto）

### 6.1 `biz.proto`（Spring ↔ Quarkus，新建）

**wire 包名必须与 Quarkus 侧一致**（`com.bigbear.quarkus.template.grpc`）：gRPC 方法路径 =
`/{package}.{Service}/{Method}`，两端 proto package 不同则无法路由。
Spring 侧的 `java_package` 可独立为 `com.bigbear.spring.grpc.biz`（仅影响本地生成的 Java 类，不影响 wire 协议）。

共享契约落点：`proto/biz/v1/biz.proto`（仓库根 proto 目录），Spring 侧新模块 `biz-api` 经
`protobuf-maven-plugin` 生成 stub（仿 `user-api`，仅 include `biz/v1/biz.proto`）：

```protobuf
syntax = "proto3";

// wire 包名必须与 Quarkus 侧 template-provider/src/main/proto/biz.proto 一致
package com.bigbear.quarkus.template.grpc;

option java_multiple_files = true;
option java_package = "com.bigbear.spring.grpc.biz";
option java_outer_classname = "BizProto";

// Biz 服务：演示跨语言 XA 事务分支（Spring 发起 → Quarkus 写 biz_tbl）。
service BizService {
  // 创建业务记录：Spring 发起方调用，Quarkus 作 XA 分支写库。
  rpc CreateBiz (CreateBizRequest) returns (CreateBizReply);
}

message CreateBizRequest {
  string name = 1;
  int32  val  = 2;
}

message CreateBizReply {
  bool success = 1;
}
```

**Spring 侧客户端**（新模块 `biz-api`，仿 `user-api`，已注册进 `xxs-spring-modules` 聚合）：
- `BizClientAutoConfiguration`：`@ImportGrpcClients(target = "quarkus", types = BizServiceGrpc.BizServiceBlockingStub.class)`；
- `BizClient`：`createBiz(name, val)` 充血方法；
- demo 中通道目标：`spring.grpc.client.channel.quarkus.target=nacos://template-provider-grpc`；
- XID 透传不在此处挂载——由 demo 全局拦截器统一覆盖（见 3.4）。

> 早期文档以 `package biz.v1` 起草——**不可用**：会与 Quarkus 侧 `com.bigbear.quarkus.template.grpc`
> 的方法路径不一致，导致 RPC 无法路由到服务。

### 6.2 `user.proto` 新增 `CreateUser`（Spring ↔ Go）

在现有 `go/bear-admin/api/protos/user/v1/user.proto` 的 `UserService` 中新增：

```protobuf
service UserService {
  rpc GetUser(GetUserRequest) returns (GetUserReply);
  // 新增：创建用户（由 Spring 发起方调用，Go 作 XA 分支写库）。
  rpc CreateUser(CreateUserRequest) returns (CreateUserReply);
}

message CreateUserRequest {
  string name = 1;
  int32  age  = 2;
}

message CreateUserReply {
  bool success = 1;
}
```

重新生成 Go stub 与 Spring `user-api` 客户端 stub。

---

## 七、环境开关（本地开发 / 测试生产切换）

> Seata 原本是启动即硬依赖（DataSource 被强制 XA 代理、进程启动时强连 TC）。
> 为支持「**本地开发关闭、测试/生产开启**」，三端统一用环境变量 `SEATA_ENABLED` 控制。

### 7.1 开关语义

| `SEATA_ENABLED` | 场景 | 行为 |
|-----------------|------|------|
| `false`（**默认**） | 本地开发 | 三端走**普通本地事务**；DataSource 不代理；不连 TC；gRPC 调用照常，无 XID 透传 |
| `true` | 测试 / 生产 | 三端启用 **XA 分布式事务**；DataSource 被 XA 代理；进程启动连 TC；XID 跨语言透传 |

关闭后不存在脏数据风险：因为根本没注册 XA 分支，各端本地事务各自直接提交。

### 7.2 三端改造点

- **Spring（发起方）** `xxs-spring-grpc-demo/src/main/resources/application.yml`
  - `spring.datasource.dynamic.seata: ${SEATA_ENABLED:false}`
  - `seata.enabled: ${SEATA_ENABLED:false}`
  - 关闭时 DataSource 退回普通 PG 连接；`@GlobalTransactional` 无 XID 时空转，gRPC 照常。
- **Go（分支）** `bear-admin`
  - `cmd/server/main.go`：`SEATA_ENABLED != "true"` 时跳过 `seataclient.InitPath(...)`，不连 TC。
  - `internal/data/user_repo.go`：`SEATA_ENABLED == "true"` 用 `SeataXAPostgresDriver`（XA），
    否则回退普通 `pgx` 驱动走本地事务。
- **Quarkus（并列分支）** `xxs-quarkus-seata` 扩展 + `template-provider`
  - `application.properties`：`seata.enabled=${SEATA_ENABLED:false}`（`SeataConfigConstants.SEATA_ENABLED`）。
  - `SeataRuntime`（`@Startup`）：`enabled=false` 跳过 `RmNettyRemotingClient.init` / `TmNettyRemotingClient.init`，不连 TC。
  - `SeataXADriver`（`db-kind=seata`）：懒构造 `DataSourceProxyXA`；`enabled=false` 时资源仅内存登记、不连 TC，连接退化为普通直连（本地提交）。
  - `SeataServerInterceptor` / `SeataClientInterceptor`（`@GlobalInterceptor`）：无 XID 时自然不绑定/不透传，无需按开关分支。
  - 注入点直接用 Agroal `DataSource`（`db-kind=seata`），**无需** `@SeataXA` 限定符（已规避 CDI 歧义）。

### 7.3 本地开发快速启动

```bash
# 本地开发（默认即可，可显式不设置或设为 false）
# 不连 TC，普通本地事务，gRPC 链路通畅
export SEATA_ENABLED=false

# 测试 / 生产环境
export SEATA_ENABLED=true
# 其余 Nacos / TC / PG(max_prepared_transactions>0) 依赖照常启动
```

### 7.4 验证开关生效

- `SEATA_ENABLED=false` 启动三端：日志**不出现** `Seata clients initialized`、`seata-go 连接 TC`；
  调用事务入口，三表各自成功插入（普通提交），放开 `1/0` 则仅 Spring 侧本地回滚（Go/Quarkus 已提交，符合预期——未开启分布式事务）。
- `SEATA_ENABLED=true` 启动三端：日志出现 Seata 初始化；放开 `1/0` 三表均回滚（XA 一致）。

---

## 八、验证步骤

1. **启动依赖**：Nacos（`xhf` 命名空间）、Seata Server（注册到 Nacos，存储库 `seata` 用 PostgreSQL）、PostgreSQL 16 实例（已配置 `max_prepared_transactions > 0`）。
2. **建库建表**：在 PG 实例上创建 `seata`（TC 存储）与 `seata_java` / `seata_go` / `seata_quarkus`（业务库），
   TC 库执行官方 `postgresql.sql`，业务库执行 `java/xxs-micro/sql/seata-xa-demo.sql`（幂等）。
3. **启动 Go `bear-admin`**：观察日志出现 seata-go 连接 TC 成功、注册 RM。
4. **启动 Quarkus `template-provider`**：`SEATA_ENABLED=true` 时观察 `Seata clients initialized`；
   调用时 debug 日志 `seata xid [...] propagated to grpc context`。
5. **启动 Spring `xxs-spring-grpc-demo`**：观察 seata 自动配置生效、dynamic-datasource 代理 master 成功。
6. **调用事务入口** `POST /demo/seata/create-user?name=xx&age=18`：
   - **成功路径**：`seata_java.biz_tbl`、`seata_go.user_tbl`、`seata_quarkus.biz_tbl` 三表各插入一条。
   - **回滚路径**：`POST /demo/seata/create-user-fail?name=xx&age=18`（三端落库后主动抛错），
     调用后**三表均无新数据**（Go + Quarkus 已写入的 XA 分支也被回滚）。
7. **TC 控制台**核对全局事务状态：成功 → `Committed`；回滚 → `Rollbacked`，分支数 = 3。

---

## 八、风险与注意

- **PostgreSQL XA 硬性前提**：实例 `postgresql.conf` 必须设置 `max_prepared_transactions > 0`（默认 0 禁用预备事务），
  否则 XA 的 `PREPARE TRANSACTION` 两阶段提交直接报错；建议设为 `200` 且 `>= max_connections` 量级，修改后重启生效。
  各端连接用户需具备 `PREPARE TRANSACTION` / `COMMIT PREPARED` 权限。
- **占位符差异**：Go 侧 `seata-go`（pgx）与 SQL 模板须用 `$1/$2` 占位符，切勿沿用 MySQL 的 `?`。
- **ctx 透传是 Go 侧成败关键**：所有 DB 写必须用 `ExecContext(ctx, ...)`，
  ctx 经由 `tm.SetXID` 绑定，否则不会成为全局事务分支（看似成功，实则未纳入）。
- **Quarkus 线程模型（reactive 安全）**：Quarkus gRPC 服务方法返回 `Uni`，业务在 worker 线程执行；
  `RootContext` 为 ThreadLocal，拦截器所在 event-loop 线程不可直接 bind（跨线程无效且有并发竞态）。
  入站拦截器只把 XID 放入 gRPC Context，业务侧在执行 DB 写前用 `SeataXidContext.runWithXid`
  在当前线程重绑、`finally` 中安全解绑。
- **不要双重代理（Spring）**：方案 B 下 `seata.enable-auto-data-source-proxy=false`，
  否则与 `dynamic-datasource.seata=true` 冲突。
- **只 JOIN 不新开**：Go/Quarkus 作分支拿到 XID 后绑定即可，**严禁** 再开新全局事务
  （Go 勿调 `tm.WithGlobalTx`，Quarkus 分支方法勿加 `@GlobalTransactional`）。
- **XID 键名一致性**：三端 XID 元数据键必须均为 `TX_XID`
  （Java/Quarkus `RootContext.KEY_XID`、Go `constant.XidKey`），任一端改名都会断链导致脏数据。
  启动时建议加断言校验 Go `constant.XidKey == "TX_XID"`。
- **seata-go 配置根键**：`seatago.yml` 顶层必须包在 `seata:` 下，否则配置不生效。
- **seata-go 模块路径**：以实际 `go get` 拉取为准，`seata.apache.org/seata-go/v2`（vanity import）
  与 `github.com/apache/incubator-seata-go/v2` 可能均可用。
- **Quarkus seata-core 手动装配**：属较前沿做法，seata-core 版本 API 可能有微调，
 上线前以实际拉取版本的 `TMClient`/`RMClient`/`DataSourceProxyXA` 签名为准；
 JVM 模式可用，native 模式需额外反射注册（demo 暂不考虑 native）。
- **Quarkus CDI 数据源（已规避歧义）**：本实现**不**额外产出 `@Default DataSource`，而是通过
  `db-kind=seata` 自定义驱动 `SeataXADriver` 接管连接供给——Agroal 池内连接即 XA 代理连接，
  业务侧注入 `DataSource` 无歧义，**无需** `@SeataXA` 限定符。
  早期 `XADataSourceProducer` + `@SeataXA` 的 producer 方案因 Agroal 自动合成 `@Default DataSource`
  会造成 `AmbiguousResolutionException`（`io.quarkus.arc.deployment.ArcProcessor#validate`），已弃用。
- **Quarkus 下 seata 配置无法读 registry.conf（已解决）**：seata-core 在 `ConfigurationFactory`
  类初始化时默认从 classpath 读取 `registry.conf` 以确定 `config.type`，但 Quarkus 的 ClassLoader
  资源隔离导致该文件**无法稳定被发现**，启动即抛
  `NotSupportYetException: config type can not be null`（`ApplicationImpl.doStart` 失败）。
  **当前做法**：不依赖文件，配置全部经 MicroProfile Config（`application.properties` 的 `seata.*` 键）承载，
  通过 Seata SPI `ExtConfigurationProvider`（`SeataConfigProvider`，动态代理）把 seata 内部裸 key 桥接为
  `seata.<dataId>`（含驼峰/中划线/下划线变体），不再使用 `System.setProperty`。
- **版本兼容**：Spring Boot 版本已确认与 `ruoyi-common-seata` 的 Seata 客户端兼容；
  seata-go / seata-core 与 Seata Server 建议对齐同主版本。
