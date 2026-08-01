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

**业务侧（Java 库 `seata_java`、Go 库 `seata_go`、Quarkus 库 `seata_quarkus`）** —— XA 模式**不需要** `undo_log`，
只需普通业务表。建表脚本见 `seata/sql/business.sql`（PostgreSQL 16 语法：`GENERATED ALWAYS AS IDENTITY` 替代 `AUTO_INCREMENT`）。
**注意**：XA 依赖 PG 预备事务，请先确认 `postgresql.conf` 中 `max_prepared_transactions > 0`（详见开头说明）。

```sql
-- Java 侧业务库 seata_java（发起方本地分支）
CREATE TABLE IF NOT EXISTS biz_tbl (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(64),
  val  INT DEFAULT 0
);

-- Go 侧业务库 seata_go
CREATE TABLE IF NOT EXISTS user_tbl (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(64),
  age  INT DEFAULT 0
);

-- Quarkus 侧业务库 seata_quarkus（并列分支, 新增）
CREATE TABLE IF NOT EXISTS biz_tbl (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(64),
  val  INT DEFAULT 0
);
```

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

### 3.3 新增 `SeataClientInterceptor`（gRPC 出站透传 XID）

仿照 `xxs-spring-grpc/.../auth/AuthClientInterceptor` 的骨架，
在 **grpc-demo 模块**内新增拦截器，把当前线程的 XID 写入 gRPC Metadata。
**该拦截器同时挂到 `user`（→Go）和 `quarkus`（→Quarkus）两个通道。**

`src/main/java/com/bigbear/spring/grpc/demo/seata/SeataClientInterceptor.java`：

```java
package com.bigbear.spring.grpc.demo.seata;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.apache.seata.core.context.RootContext;

/**
 * gRPC 客户端拦截器：将 Seata 全局事务 XID 透传至下游（Go / Quarkus 服务）。
 *
 * <p>在 {@code @GlobalTransactional} 标注的方法内，{@link RootContext} 持有当前 XID；
 * 拦截器于请求发出前将其写入 {@code Metadata[TX_XID]}，下游据此 JOIN 同一全局事务。
 */
public class SeataClientInterceptor implements ClientInterceptor {

    /** Seata XID 元数据键，须与 Go {@code constant.XidKey}、Quarkus {@code RootContext.KEY_XID} 一致（"TX_XID"）。 */
    private static final Metadata.Key<String> XID_KEY =
            Metadata.Key.of(RootContext.KEY_XID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, options)) {
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

### 3.4 把拦截器挂到 `user` 与 `quarkus` 两个通道

修改 `user-api/.../UserClientAutoConfiguration.java`（挂 `user` 通道），
并在 `biz-api/.../BizClientAutoConfiguration.java`（挂 `quarkus` 通道）同样挂载：

```java
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(
    target = "user",
    types = UserServiceGrpc.UserServiceBlockingStub.class,
    intercept = SeataClientInterceptor.class   // 出站透传 XID
)
public class UserClientAutoConfiguration {
    @Bean
    public UserClient userClient(UserServiceBlockingStub stub) {
        return new UserClient(stub);
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
@ImportGrpcClients(
    target = "quarkus",
    types = BizServiceGrpc.BizServiceBlockingStub.class,
    intercept = SeataClientInterceptor.class   // 出站透传 XID（同一拦截器复用）
)
public class BizClientAutoConfiguration {
    @Bean
    public BizClient bizClient(BizServiceBlockingStub stub) {
        return new BizClient(stub);
    }
}
```

### 3.5 新增事务编排服务（本地写库 + gRPC 调 Go + gRPC 调 Quarkus）

在 demo 模块新增一个充血服务，使用 `@GlobalTransactional` 把三段写入纳入同一事务。

`src/main/java/com/bigbear/spring/grpc/demo/BizTransactionService.java`：

```java
package com.bigbear.spring.grpc.demo;

import com.bigbear.spring.grpc.user.UserClient;
import com.bigbear.spring.grpc.biz.BizClient;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 分布式事务编排：演示「本地写库 + 跨语言 gRPC 写库(Go + Quarkus)」同一 XA 事务。
 *
 * <p>三段分支任一抛异常，Java/Go/Quarkus 两侧 XA 分支均回滚。
 */
@Service
@RequiredArgsConstructor
public class BizTransactionService {

    /** 指向 dynamic-datasource 代理后的 master（seata_java 库）。 */
    private final JdbcTemplate jdbcTemplate;
    private final UserClient userClient;
    private final BizClient bizClient;

    /**
     * 开启全局事务：先写本地 biz_tbl，再经 gRPC 调用 Go 写 user_tbl、调 Quarkus 写 biz_tbl。
     *
     * @param name 业务名
     * @return 结果摘要
     */
    @GlobalTransactional(name = "demo-create-biz", rollbackFor = Exception.class)
    public String createBiz(String name) {
        // 1) 本地分支（XA）
        jdbcTemplate.update("INSERT INTO biz_tbl(name, val) VALUES (?, ?)", name, 1);

        // 2) 跨语言分支 A：gRPC → Go，拦截器自动透传 XID
        userClient.createUser(name, 18);

        // 3) 跨语言分支 B：gRPC → Quarkus，拦截器自动透传 XID
        bizClient.createBiz(name, 2);

        // 演示回滚：放开下行，整个全局事务将回滚（含 Go + Quarkus 侧已写入）
        // int i = 1 / 0;
        return "ok:" + name;
    }
}
```

> `UserClient` 需补 `createUser(name, age)`；`BizClient` 提供 `createBiz(name, val)`，对应 proto 见第六章。

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
> 均为 Spring 专属，Quarkus（CDI 体系）无法直接复用。方案：用 `seata-core`（非 `seata-all`，避免拉入 Spring）
> 手动 CDI 装配 —— TM/RM 初始化 + XA 数据源代理 + gRPC 入站 XID 绑定。
>
> 落点：新建公共模块 `xxs-quarkus-seata`，再集成进 `xxs-quarkus-template/template-provider`。

### 5.1 BOM 增加 seata 版本管理

`java/xxs-micro/xxs-common-quarkus/xxs-quarkus-bom/pom.xml` 的 `dependencyManagement` 增加：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 新增：apache seata-core（排除任何传递的 spring-* 依赖，避免污染 Quarkus） -->
        <dependency>
            <groupId>org.apache.seata</groupId>
            <artifactId>seata-core</artifactId>
            <version>${seata.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>org.springframework</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> `seata.version` 与 Spring 侧 `ruoyi-common-seata` 使用的 apache seata 版本对齐（建议同主版本）。

### 5.2 新建公共模块 `xxs-quarkus-seata`

在 `java/xxs-micro/xxs-common-quarkus/xxs-quarkus-modules/` 下新增子模块 `xxs-quarkus-seata`，
并在 `xxs-quarkus-modules/pom.xml` 的 `<modules>` 聚合它。

`xxs-quarkus-modules/xxs-quarkus-seata/pom.xml`：

```xml
<dependencies>
    <dependency>
        <groupId>com.bigbear.xxs</groupId>
        <artifactId>xxs-quarkus-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.bigbear.xxs</groupId>
        <artifactId>xxs-quarkus-grpc</artifactId>
    </dependency>
    <!-- seata-core（CDI 手动装配，非 spring-boot-starter） -->
    <dependency>
        <groupId>org.apache.seata</groupId>
        <artifactId>seata-core</artifactId>
    </dependency>
    <!-- PostgreSQL XA 驱动（含 pgx 与 XA 能力，由 Quarkus 托管） -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
</dependencies>
```

模块内提供以下组件（包名 `com.bigbear.quarkus.seata`）。

#### 5.2.1 `SeataProperties` —— 配置映射

```java
package com.bigbear.quarkus.seata;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Seata 客户端配置映射（对齐 Spring 侧 seata.* 配置）。
 *
 * <p>Quarkus 运行时 seata-core 无法稳定发现 classpath 内的 registry.conf（ClassLoader 资源隔离），
 * 故所有 registry/config 参数改由本映射接收，并在 SeataBootstrap 中映射为 seata 系统属性。
 */
@ConfigMapping(prefix = "seata")
public interface SeataProperties {

    /** 环境总开关：本地开发默认 false（走普通本地事务、不连 TC），测试/生产设为 true。 */
    @ConfigProperty(name = "enabled", defaultValue = "false")
    boolean enabled();

    /** 应用标识。 */
    @ConfigProperty(name = "application-id", defaultValue = "quarkus-app")
    String applicationId();

    /** 事务分组。 */
    @ConfigProperty(name = "tx-service-group", defaultValue = "default_tx_group")
    String txServiceGroup();

    /** vgroup → cluster 映射。 */
    @WithName("service.vgroup-mapping")
    Map<String, String> vgroupMapping();

    /** cluster → host:port TC 地址列表。 */
    @WithName("service.grouplist")
    Map<String, String> grouplist();

    /** Nacos 注册/配置中心连接信息。 */
    NacosConfig nacos();

    /** Nacos 连接配置。 */
    interface NacosConfig {
        @ConfigProperty(name = "server-addr", defaultValue = "127.0.0.1:8848")
        String serverAddr();
        @ConfigProperty(name = "namespace", defaultValue = "xhf")
        String namespace();
        @ConfigProperty(name = "group", defaultValue = "DEFAULT_GROUP")
        String group();
        @ConfigProperty(name = "application", defaultValue = "seata-server")
        String application();
        @ConfigProperty(name = "username", defaultValue = "nacos")
        String username();
        @ConfigProperty(name = "password", defaultValue = "nacos")
        String password();
        @ConfigProperty(name = "data-id", defaultValue = "seata-server.properties")
        String dataId();
    }
}
```

#### 5.2.2 `SeataBootstrap` —— TM/RM 初始化

```java
package com.bigbear.quarkus.seata;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.seata.rm.RMClient;
import org.apache.seata.tm.TMClient;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Seata 客户端启动初始化：在 Quarkus 启动时初始化 TM 与 RM。
 *
 * <p>Quarkus 无 seata-spring-boot-starter 自动装配，故手动调用
 * {@link TMClient#init} / {@link RMClient#init} 注册到 TC。
 *
 * <p>Quarkus 运行时 seata-core 无法稳定发现 classpath 内的 registry.conf（ClassLoader 资源隔离），
 * 因此本类在初始化前将配置以 seata 系统属性形式注入（seata 优先读取系统属性，覆盖文件发现），
 * 规避 {@code config type can not be null} 启动异常。
 */
@ApplicationScoped
public class SeataBootstrap {

    private static final Logger LOG = Logger.getLogger(SeataBootstrap.class);

    @Inject
    SeataProperties props;

    /**
     * 应用启动时初始化 Seata TM/RM 客户端。
     *
     * <p>调用 {@link TMClient#init} 会触发 seata-core ConfigurationFactory 类初始化，
     * 故必须在它之前完成系统属性注入，否则报 {@code config type can not be null}。
     *
     * @param ev 启动事件
     */
    void onStart(@Observes StartupEvent ev) {
        // 环境开关关闭：跳过 TM/RM 初始化，本服务走普通本地事务，不连 TC。
        if (!props.enabled()) {
            LOG.info("Seata disabled by config (seata.enabled=false), skip TM/RM init.");
            return;
        }

        // 关键：在 TMClient.init 触发 seata 配置类初始化前，注入 seata 系统属性
        applySeataSystemProperties();

        String appId = props.applicationId();
        String txGroup = props.txServiceGroup();
        LOG.infof("Seata init: appId=%s, txServiceGroup=%s", appId, txGroup);

        TMClient.init(appId, txGroup);
        RMClient.init(appId, txGroup);
        LOG.info("Seata TM/RM initialized.");
    }

    /** 将 Quarkus 配置映射为 seata-core 可识别的 seata.* 系统属性（绕过 registry.conf 文件发现）。 */
    private void applySeataSystemProperties() {
        SeataProperties.NacosConfig nacos = props.nacos();
        setIfAbsent("seata.registry.type", "nacos");
        setIfAbsent("seata.registry.nacos.application", nacos.application());
        setIfAbsent("seata.registry.nacos.serverAddr", nacos.serverAddr());
        setIfAbsent("seata.registry.nacos.group", nacos.group());
        setIfAbsent("seata.registry.nacos.namespace", nacos.namespace());
        setIfAbsent("seata.registry.nacos.username", nacos.username());
        setIfAbsent("seata.registry.nacos.password", nacos.password());

        setIfAbsent("seata.config.type", "nacos");
        setIfAbsent("seata.config.nacos.serverAddr", nacos.serverAddr());
        setIfAbsent("seata.config.nacos.group", nacos.group());
        setIfAbsent("seata.config.nacos.namespace", nacos.namespace());
        setIfAbsent("seata.config.nacos.dataId", nacos.dataId());
        setIfAbsent("seata.config.nacos.username", nacos.username());
        setIfAbsent("seata.config.nacos.password", nacos.password());

        if (props.vgroupMapping() != null) {
            props.vgroupMapping().forEach((g, c) -> setIfAbsent("seata.service.vgroupMapping." + g, c));
        }
        if (props.grouplist() != null) {
            props.grouplist().forEach((c, a) -> setIfAbsent("seata.service.grouplist." + c, a));
        }
    }

    /** 仅在系统属性未设置时写入，避免覆盖外部显式配置。 */
    private void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null && value != null) {
            System.setProperty(key, value);
        }
    }
}
```

> **关键坑（Quarkus 专属）**：seata-core 在 `ConfigurationFactory` 类初始化时读取配置，默认依赖
> classpath 内的 `registry.conf` 确定 `config.type`。Quarkus 的 ClassLoader 资源隔离导致该文件
> **无法被稳定发现**，进而抛 `NotSupportYetException: config type can not be null`，阻断启动。
> 解决方式：不依赖文件，改为在 `TMClient.init` 之前用 `System.setProperty("seata.*", ...)` 注入
> （seata 优先读系统属性）。`application.properties` 中 `seata.nacos.*` 提供 Nacos 连接信息，
> 经 `SeataProperties` 映射后写入系统属性。
> 模块 resources 下的 `registry.conf` 可作为兜底保留，但不再作为主路径。

#### 5.2.3 `SeataXA` —— 限定注解（消除 CDI 歧义）

Quarkus Agroal 会依据 `quarkus.datasource.*` **自动合成**一个带 `@Default` 限定、含连接池的
`AgroalDataSource`。若本 producer 再产出 `@Default DataSource`，注入点 `DataSource` 将出现两个
`@Default` 候选（Agroal 合成 bean + 本 producer bean），启动即抛
`AmbiguousResolutionException`。

故新增自定义限定注解 `@SeataXA`，把 Seata 数据源与 Agroal 默认数据源隔离，业务侧以 `@SeataXA`
精确注入。

```java
package com.bigbear.quarkus.seata;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 限定 Seata XA 代理数据源，与 Agroal 默认 {@code @Default DataSource} 区分，避免 CDI 歧义解析。
 */
@Qualifier
@Documented
@Retention(RUNTIME)
@Target({TYPE, FIELD, PARAMETER})
public @interface SeataXA {
}
```

#### 5.2.4 `XADataSourceProducer` —— XA 数据源代理（复用 Agroal 连接池）

```java
package com.bigbear.quarkus.seata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.apache.seata.rm.datasource.xa.DataSourceProxyXA;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.sql.DataSource;

/**
 * 生产 Seata XA 代理数据源。
 *
 * <p>Quarkus Agroal 已依据 {@code quarkus.datasource.*} 自动合成带连接池的默认
 * {@code @Default DataSource}，本类<b>复用</b>该连接池（而非另建裸连接）。
 * 开启 Seata 时将其包装为 {@link DataSourceProxyXA}，DB 写自动成为 XA 分支；
 * 关闭时直接透传 Agroal 原生 DataSource（本地提交，不连 TC）。
 *
 * <p>产出的 Bean 带 {@link SeataXA} 限定注解，与 Agroal 默认 {@code @Default DataSource}
 * 隔离，业务侧以 {@code @SeataXA} 精确注入，避免 CDI 歧义解析。
 */
@ApplicationScoped
public class XADataSourceProducer {

    private static final Logger LOG = Logger.getLogger(XADataSourceProducer.class);

    /**
     * Agroal 自动合成的默认 DataSource（已含连接池，带 @Default 限定）。
     * 不带 {@link SeataXA} 限定，避免与本类产出的 Bean 形成循环依赖。
     */
    @Inject
    DataSource agroalDataSource;

    @Inject
    SeataProperties props;

    /**
     * 产出 Seata XA 代理数据源（带 {@link SeataXA} 限定）。
     *
     * <p>受 {@code seata.enabled} 环境开关控制：
     * <ul>
     *   <li>true：以 Agroal 原生 DataSource 为底层，包装为 {@link DataSourceProxyXA}，
     *       DB 写自动纳入全局事务 XA 分支；</li>
     *   <li>false（默认，本地开发）：直接透传 Agroal 原生 DataSource（本地提交，不连 TC）。</li>
     * </ul>
     *
     * <p>复用 Agroal 连接池，避免裸连接无池化；带 {@link SeataXA} 限定保证注入类型唯一。
     *
     * @return 数据源（XA 代理或 Agroal 原生 DataSource）
     */
    @Produces
    @ApplicationScoped
    @SeataXA
    public DataSource seataDataSource() {
        // 关闭 Seata：直接透传 Agroal 原生 DataSource，走本地提交，无需 XA 分支注册
        if (!props.enabled()) {
            LOG.infof("Seata disabled, native Agroal datasource used (url from quarkus.datasource)");
            return agroalDataSource;
        }

        LOG.infof("Seata XA datasource created (based on Agroal pool)");
        // 包装为 Seata XA 代理，DB 写即自动成为 XA 分支
        return new DataSourceProxyXA(agroalDataSource);
    }
}
```

> 关键设计：**不再 `new PGXADataSource()` 另建裸连接**，而是注入 Agroal 默认 `DataSource`（含连接池）
> 后复用为底层。这样 Seata 代理也享受连接池，避免裸连接无池化、连接泄漏风险。
> PG XA 同样依赖实例 `max_prepared_transactions > 0`。

#### 5.2.5 `SeataServerInterceptor` —— gRPC 入站绑定 XID

仿照现有 `xxs-quarkus-grpc/.../config/MetadataInterceptor` 的 `@GlobalInterceptor` 范式：
入站读 `TX_XID` → `RootContext.bind`，回调结束 → `RootContext.unbind`。

```java
package com.bigbear.quarkus.seata;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.inject.Singleton;
import org.apache.seata.core.context.RootContext;
import org.jboss.logging.Logger;

/**
 * gRPC 服务端全局拦截器：入站绑定 Seata XID，使本服务 JOIN 上游全局事务。
 *
 * <p>Quarkus 作并列分支，仅需入站绑定即可（无需发起）；DB 写经 XA 代理自动注册为分支。
 * gRPC 在 Quarkus 下运行于 vert.x event loop，{@link RootContext} 为 ThreadLocal，
 * 故在拦截器入口 bind、调用结束（close/cancel）时 unbind，避免线程复用串数据。
 */
@Singleton
@GlobalInterceptor
public class SeataServerInterceptor implements ServerInterceptor {

    private static final Logger LOG = Logger.getLogger(SeataServerInterceptor.class);

    /** XID 元数据键，须与 Java RootContext.KEY_XID、Go constant.XidKey 一致。 */
    private static final Metadata.Key<String> XID_KEY =
            Metadata.Key.of(RootContext.KEY_XID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String xid = headers.get(XID_KEY);
        if (xid != null && !xid.isEmpty()) {
            RootContext.bind(xid);
            LOG.debugf("Seata XID bound: %s", xid);
        }

        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

        // 回调结束时清理 ThreadLocal，防止线程复用串数据
        return new ForwardingServerCallListener<ReqT>() {
            @Override
            public void onComplete() {
                cleanup(xid);
                super.onComplete();
            }
            @Override
            public void onCancel() {
                cleanup(xid);
                super.onCancel();
            }

            private void cleanup(String bound) {
                if (bound != null && !bound.isEmpty()) {
                    RootContext.unbind();
                }
            }
        };
    }
}
```

> `ForwardingServerCallListener` 取 `io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener`。
> 实现 XA 分支提交/回滚由 seata-core RM 在连接 close 时自动驱动，业务无需手动 `commit`。

#### 5.2.6 `SeataClientInterceptor` —— 出站透传 XID（能力储备）

本场景 Quarkus 作并列分支不出站，但保留出站拦截器以备未来 Quarkus 再调下游。

```java
package com.bigbear.quarkus.seata;

import io.grpc.*;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.inject.Singleton;
import org.apache.seata.core.context.RootContext;

/**
 * gRPC 客户端全局拦截器：出站把当前 XID 写入 Metadata（能力储备）。
 */
@Singleton
@GlobalInterceptor
public class SeataClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> XID_KEY =
            Metadata.Key.of(RootContext.KEY_XID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
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

### 5.3 集成进 `template-provider`

`java/xxs-micro/xxs-quarkus-template/template-provider/pom.xml` 增加依赖：

```xml
<dependencies>
    <!-- 现有：quarkus-grpc / config-yaml / health / config-nacos / xxs-quarkus-nacos-discovery ... -->

    <!-- 新增：seata 公共能力 -->
    <dependency>
        <groupId>com.bigbear.xxs</groupId>
        <artifactId>xxs-quarkus-seata</artifactId>
    </dependency>
    <!-- MyBatis-Flex（XA 代理 DataSource 注入 Flex） -->
    <dependency>
        <groupId>com.bigbear.xxs</groupId>
        <artifactId>xxs-quarkus-mybatis</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
</dependencies>
```

`template-provider/src/main/resources/application.properties` 增加 datasource 与 seata 配置：

```properties
# 现有 Nacos / Stork / grpc.server.port=9090 配置保持 ...

# ===== 新增：数据源（指向 seata_quarkus 库，由 XADataSourceProducer 代理） =====
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://192.168.50.45:5432/seata_quarkus?currentSchema=public
quarkus.datasource.username=root
quarkus.datasource.password=123456

# ===== 新增：Seata（对齐 Spring 侧） =====
seata.application-id=template-provider
seata.tx-service-group=default_tx_group
seata.service.vgroup-mapping.default_tx_group=default
seata.service.grouplist.default=127.0.0.1:8091
quarkus.application.name=template-provider
```

并在 `src/main/resources` 放置 `registry.conf`（指向 Nacos `xhf`），例：

```
registry {
  type = "nacos"
  nacos {
    application = "seata-server"
    serverAddr = "127.0.0.1:8848"
    group = "DEFAULT_GROUP"
    namespace = "xhf"
  }
}
config {
  type = "nacos"
  nacos {
    serverAddr = "127.0.0.1:8848"
    group = "DEFAULT_GROUP"
    namespace = "xhf"
    dataId = "seata-server.properties"
  }
}
```

### 5.4 新增 `biz.proto` 与 `BizService` 实现

`template-provider/src/main/proto/biz.proto`（见第六章共享契约）。Quarkus 用 `quarkus-maven-plugin`
的 `generate-code` goal 自动生成服务端 stub。

`template-provider/src/main/java/com/bigbear/quarkus/template/provider/BizServiceImpl.java`：

```java
package com.bigbear.quarkus.template.provider;

import com.bigbear.quarkus.seata.SeataXA;
import com.bigbear.quarkus.template.grpc.BizService;
import com.bigbear.quarkus.template.grpc.CreateBizReply;
import com.bigbear.quarkus.template.grpc.CreateBizRequest;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.apache.seata.core.context.RootContext;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * BizService 实现：在全局事务分支内写入 biz_tbl。
 *
 * <p>XID 由 {@code SeataServerInterceptor} 入站绑定到 RootContext；数据源为 XA 代理
 * （{@code DataSourceProxyXA}），本地写自动注册为该全局事务的 XA 分支。
 */
@GrpcService
public class BizServiceImpl implements BizService {

    private static final Logger LOG = Logger.getLogger(BizServiceImpl.class);

    /** 注入的 DataSource 为 Seata XA 代理（由 XADataSourceProducer 产出，带 {@link SeataXA} 限定）。 */
    private final DataSource dataSource;

    /**
     * 构造 Biz 服务实现。
     *
     * @param dataSource Seata XA 代理数据源
     */
    public BizServiceImpl(@SeataXA DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 创建业务记录（XA 分支）。
     *
     * <p>仅当入站携带全局事务 XID（本服务 JOIN 上游事务）时，本次写库才注册为 XA 分支；
     * 无事务调用退化为普通本地写，不产生副作用。
     *
     * @param request 请求
     * @return 创建结果
     */
    @Override
    public Uni<CreateBizReply> createBiz(CreateBizRequest request) {
        LOG.infof("createBiz, name=%s, val=%d, xid=%s",
            request.getName(), request.getVal(), RootContext.getXID());

        return Uni.createFrom().item(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO biz_tbl(name, val) VALUES (?, ?)")) {
                ps.setString(1, request.getName());
                ps.setInt(2, request.getVal());
                ps.executeUpdate();
            } catch (Exception e) {
                // 抛异常使 gRPC 调用失败 → 发起方全局事务整体回滚
                LOG.errorf(e, "createBiz failed, name=%s", request.getName());
                throw new IllegalStateException("createBiz failed: " + request.getName(), e);
            }
            return CreateBizReply.newBuilder().setSuccess(true).build();
        });
    }
}
```

> 注入点必须用 `@SeataXA` 精确选择 Seata 代理数据源，否则会与 Agroal 默认 `@Default DataSource`
> 形成歧义（见 5.2.3 与第七章风险说明）。`GrpcService` 的 service 接口按实际生成类型
> （此处为 `BizService`）实现，使用 Mutiny 响应式 `Uni` 返回值。

---

## 六、共享契约（proto）

### 6.1 `biz.proto`（Spring ↔ Quarkus，新建）

新建独立 `biz-api` 模块（Spring 客户端用）与 Quarkus `template-provider/proto` 各放一份相同内容：

```protobuf
syntax = "proto3";

package biz.v1;

option java_package = "com.bigbear.spring.grpc.biz";
option java_multiple_files = true;

// 业务服务：演示跨语言 XA 事务分支。
service BizService {
  // 创建业务记录（由 Spring 发起方调用，Quarkus 作 XA 分支写库）。
  rpc CreateBiz(CreateBizRequest) returns (CreateBizReply);
}

message CreateBizRequest {
  string name = 1;
  int32  val  = 2;
}

message CreateBizReply {
  bool success = 1;
}
```

> Quarkus 端 `java_package` 可按需调整为 `com.bigbear.quarkus.template.grpc.biz`，但两端 proto 字段定义须一致。
> Spring 用 `protobuf-maven-plugin` 生成客户端 stub（参照现有 `user-api` 模块范式）。

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
- **Quarkus（并列分支）** `xxs-quarkus-seata` 模块 + `template-provider`
  - `SeataProperties.enabled()`：默认 `false`。
  - `application.properties`：`seata.enabled=${SEATA_ENABLED:false}`。
  - `SeataBootstrap.onStart`：`enabled=false` 跳过 `TMClient.init` / `RMClient.init`。
  - `XADataSourceProducer.seataDataSource`：`enabled=false` 直接透传 Agroal 原生 `DataSource`（普通提交）；`true` 包 `DataSourceProxyXA`。
  - 注入点必须用 `@SeataXA` 限定（如 `BizServiceImpl` 构造函数），与 Agroal 默认 `@Default DataSource` 隔离，否则启动报 `AmbiguousResolutionException`。
  - `SeataServerInterceptor`：`enabled=false` 跳过 XID 绑定/解绑。

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

- `SEATA_ENABLED=false` 启动三端：日志**不出现** `Seata TM/RM initialized.`、`seata-go 连接 TC`；
  调用事务入口，三表各自成功插入（普通提交），放开 `1/0` 则仅 Spring 侧本地回滚（Go/Quarkus 已提交，符合预期——未开启分布式事务）。
- `SEATA_ENABLED=true` 启动三端：日志出现 Seata 初始化；放开 `1/0` 三表均回滚（XA 一致）。

---

## 八、验证步骤

1. **启动依赖**：Nacos（`xhf` 命名空间）、Seata Server（注册到 Nacos，存储库 `seata` 用 PostgreSQL）、PostgreSQL 16 实例（已配置 `max_prepared_transactions > 0`）。
2. **建库建表**：在 PG 实例上创建 `seata` / `seata_java` / `seata_go` / `seata_quarkus` 四个 database，分别执行官方 `postgresql.sql` 与 `seata/sql/business.sql`。
3. **启动 Go `bear-admin`**：观察日志出现 seata-go 连接 TC 成功、注册 RM。
4. **启动 Quarkus `template-provider`**：观察日志 `Seata TM/RM initialized.`、`XID bound` 在调用时出现。
5. **启动 Spring `xxs-spring-grpc-demo`**：观察 seata 自动配置生效、dynamic-datasource 代理 master 成功。
6. **调用事务入口** `BizTransactionService.createBiz(name)`：
   - **成功路径**：`seata_java.biz_tbl`、`seata_go.user_tbl`、`seata_quarkus.biz_tbl` 三表各插入一条。
   - **回滚路径**：放开 `createBiz` 中 `1/0`，调用后**三表均无新数据**
     （Go + Quarkus 已写入的 XA 分支也被回滚）。
7. **TC 控制台**核对全局事务状态：成功 → `Committed`；回滚 → `Rollbacked`，分支数 = 3。

---

## 八、风险与注意

- **PostgreSQL XA 硬性前提**：实例 `postgresql.conf` 必须设置 `max_prepared_transactions > 0`（默认 0 禁用预备事务），
  否则 XA 的 `PREPARE TRANSACTION` 两阶段提交直接报错；建议设为 `200` 且 `>= max_connections` 量级，修改后重启生效。
  各端连接用户需具备 `PREPARE TRANSACTION` / `COMMIT PREPARED` 权限。
- **占位符差异**：Go 侧 `seata-go`（pgx）与 SQL 模板须用 `$1/$2` 占位符，切勿沿用 MySQL 的 `?`。
- **ctx 透传是 Go 侧成败关键**：所有 DB 写必须用 `ExecContext(ctx, ...)`，
  ctx 经由 `tm.SetXID` 绑定，否则不会成为全局事务分支（看似成功，实则未纳入）。
- **Quarkus 线程模型**：Quarkus gRPC 运行于 vert.x event loop，`RootContext` 为 ThreadLocal，
  入站拦截器必须 bind、回调结束必须 unbind，避免线程复用串数据。
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
- **Quarkus CDI 数据源歧义（必踩坑）**：Agroal 会依据 `quarkus.datasource.*` 自动合成一个带
 `@Default` 限定的 `DataSource`。若 `XADataSourceProducer` 也产出 `@Default DataSource`，
 注入点 `DataSource` 将出现两个 `@Default` 候选，启动即抛 `AmbiguousResolutionException`
 （`io.quarkus.arc.deployment.ArcProcessor#validate`）。
 **正确做法**：producer 产出带自定义 `@SeataXA` 限定的 Bean，且注入 Agroal 默认 `DataSource`
 （不带任何限定）作为底层复用连接池；业务侧以 `@SeataXA` 精确注入。
 切勿在 producer 中用 `@Inject @SeataXA DataSource` 注入自身产出 Bean，否则形成自循环依赖。
- **Quarkus 下 seata 配置无法读 registry.conf（必踩坑）**：seata-core 在 `ConfigurationFactory`
 类初始化时默认从 classpath 读取 `registry.conf` 以确定 `config.type`，但 Quarkus 的 ClassLoader
 资源隔离导致该文件**无法稳定被发现**，启动即抛
 `NotSupportYetException: config type can not be null`（`ApplicationImpl.doStart` 失败）。
 **正确做法**：不依赖 `registry.conf` 文件发现，改为在 `TMClient.init` 之前用
 `System.setProperty("seata.registry.type", ...)`、`seata.config.type`、`seata.registry.nacos.*`、
 `seata.config.nacos.*`、`seata.service.vgroupMapping.*` 等系统属性注入（seata 优先读系统属性）。
 具体由 `SeataProperties` + `SeataBootstrap.applySeataSystemProperties()` 完成，Nacos 连接信息
 来自 `application.properties` 的 `seata.nacos.*`（含默认值，与 registry.conf 对齐）。
- **版本兼容**：Spring Boot 版本已确认与 `ruoyi-common-seata` 的 Seata 客户端兼容；
  seata-go / seata-core 与 Seata Server 建议对齐同主版本。
