# 实现提示词：Spring Cloud Gateway + Casbin 网关统一鉴权（网关只鉴权版）

> 本文件是交给"实现端 AI"的落地指令。按本文件实现 `xxs-micro/xxs-component/xxs-gateway` 模块，
> 目标：基于 Spring Cloud Gateway 集成 Casbin（jcasbin），在网关层做**统一鉴权**。
> 交付物是一个**可复用 Starter**（自动装配 + 配置属性 + SPI 扩展点），供各业务服务接入。
>
> **本版本定位：网关只做鉴权，不管理角色/权限/用户。**
> 用户与角色由 **Keycloak**（OAuth2 / OIDC IdP）托管；登录/令牌服务将 `userId + 角色` 写入 Redis；
> 角色→路径→方法的 `p` 规则由外部系统写入 `casbin_rule`。网关只负责读取并 `enforce`。

---

## 0. 硬约束（必读，违反即返工）

1. **坐标与版本**
   - 根坐标 `groupId=com.bigbear`，全局版本 `${revision}=1.0.0`（由 `flatten-maven-plugin` 解析，子模块直接继承 parent，**不要**写死版本号）。
   - `xxs-gateway` 的 parent 为 `com.bigbear:xxs-component:1.0.0`。
   - Java **25**，`maven.compiler.source/target=25`，编译必须带 `-parameters`（见第 4 节 pom）。
   - 技术栈版本（**由 `xxs-gateway` 自己导入 BOM**，根 `pom.xml` 与 `xxs-dependencies` 未管理）：
     - `spring-boot-dependencies` = **4.1.0**
     - `spring-cloud-dependencies` = **2025.1.2**
     - `spring-cloud-alibaba-dependencies` = **2025.1.0.0**（仅在用 Nacos 等组件时需要，按需引入）
2. **响应式非阻塞**：Gateway 运行在 WebFlux/Netty 上，**严禁**引入 `spring-boot-starter-web`（servlet 冲突）。所有阻塞调用（Redis 取用户、DB、Casbin `enforce`）必须用 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` 包裹，**绝不允许阻塞 event loop**。
   - **不要**引入 `spring-boot-starter-oauth2-resource-server` 与 `spring-security-oauth2-jose`（任何 OAuth2 / 安全过滤器链）。网关**不验签、不解析 JWT**：Keycloak 颁发的 `access_token`（本身是 JWT）被当作**不透明引用令牌（opaque token）**，网关仅拿 `sha256(rawToken)` 去 Redis 查用户态；JWT 的验签与声明解析（sub / realm_access.roles / jti / exp）由外部**登录/令牌服务**完成，网关运行时零 Keycloak、零 oauth2 依赖。
3. **可用公共依赖**（已在 `xxs-dependencies` 管理版本，直接引用不写版本）：
   - `com.mybatis-flex:mybatis-flex-spring-boot4-starter`（`flex.version=1.11.7`）
   - `cn.hutool:hutool-core`、`cn.hutool:hutool-http`
   - `org.projectlombok:lombok`（provided）、`org.mapstruct:mapstruct`
4. **需新增到 `xxs-dependencies` 的版本登记**（在 `xxs-dependencies/pom.xml` 的 `dependencyManagement` 内新增，统一管理）：
   - `org.casbin:jcasbin`（登记一个合理稳定版本，如 `1.40.1`）
   - `com.github.ben-manes.caffeine:caffeine`
   - （无需登记 `spring-security-oauth2-jose`：网关已确定**不引入**任何 oauth2/JWT 验签依赖，见第 0 节硬约束第 2 条）
5. **包名约定**：所有代码放在 `com.bigbear.gateway.auth` 下，按第 9 节目录组织。

---

## 1. 总体架构

```
请求 → [CasbinAuthorizationGlobalFilter 鉴权]
        ├─ 白名单放行（/auth/**、/actuator/**、OPTIONS、静态资源）
        ├─ 提取 Authorization: Bearer <token>
        ├─ 取原始 Bearer token → sha256 → 查 Redis → TokenUserInfo(userId + roles[] + status)
        ├─ 不验签、不解析 JWT；rawToken 由 Keycloak 颁发，网关当作不透明串
        │        └─ Redis 缺失 / 已过期 → 返回 401（未认证）
        ├─ 规范化请求 path（去 query）+ method（大写）
        ├─ 对每个 role 调 enforcer.enforce(role, path, method)，命中任一即通过
        │        └─ 走多级缓存（Caffeine → Redis → DB 回源 casbin_rule 的 p 规则）
        └─ 放行 / 401(未认证) / 403(无权限)
                         ↑
  外部系统负责写入（网关不管理）：
    · Keycloak 托管用户/角色；登录/令牌服务把 userId+roles 写入 Redis（key=sha256(token)）
    · 角色→路径→方法的 p 规则由管理后台写入 casbin_rule(p 表)
    · DB 变更后由管理后台发 Redis Pub/Sub 广播 → 网关 Watcher 集群 reload
```

- **网关职责边界**：仅认证（验签 + Redis 取用户/角色）+ 鉴权（casbin `enforce`）。**不提供**角色/权限/用户的增删改查接口，也不含 `CasbinPolicyService` / `CasbinPolicyController` / 业务 DTO / `CasbinPolicyConverter`。
- **唯一真相源（规则）**：数据库 `casbin_rule` 扁平表，仅存 `p` 规则（`p, roleKey, path, method`）。
- **用户/角色真相源**：Keycloak（IdP）。运行时用户态在 Redis（`token → userId+roles`）。
- **多级缓存**：Caffeine（本地 JVM）→ Redis（分布式）→ DB 回源并回填。
- **热更新**：Redis Watcher（Pub/Sub），外部改策略后广播，所有网关节点 `loadPolicy` 重载。
- **线程安全**：使用 `SyncedEnforcer`（jcasbin 自带同步锁的 Enforcer）。

---

## 2. Casbin 模型（model.conf）—— 仅 p 规则，无 g（RBAC 的角色由 Redis 提供）

角色从 Redis/token 获取，因此模型**不再需要 `g` 规则与 `role_definition`**；`p.sub` 直接是角色 key。

```ini
[request_definition]
r = sub, obj, act

[policy_definition]
p = sub, obj, act

[policy_effect]
e = some(where (p.eft == allow))

[matchers]
m = r.sub == p.sub && r.obj == p.obj && r.act == p.act
```

- `r.sub` = **角色 key**（对每个角色逐一 `enforce`，见第 7 节）
- `r.obj` = 请求路径（规范化：去 query、可配置是否忽略尾斜杠）
- `r.act` = HTTP 方法（GET/POST/...，统一大写）
- `p.sub` = 角色 key（如 `admin`、`order:query`）
- `p.obj` = 资源路径（如 `/api/order/**` 或精确 `/api/order/list`）
- `p.act` = HTTP 方法（如 `GET`）
- **无 `g` 规则**：用户→角色的映射不进 casbin，由 Redis 的 `TokenUserInfo.roles` 提供。

---

## 3. 数据表（DB 规则唯一真相源，方案 A）

扁平规则表，仅 `p` 规则。`ptype` 固定取 `p`，`v0..v5` 按模型顺序存放（本项目只用 v0=role、v1=path、v2=method）。

```sql
-- schema.sql（随 Starter 提供，放在 src/main/resources）
CREATE TABLE IF NOT EXISTS casbin_rule (
    id      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ptype   VARCHAR(16)  NOT NULL,
    v0      VARCHAR(128) DEFAULT '',
    v1      VARCHAR(128) DEFAULT '',
    v2      VARCHAR(128) DEFAULT '',
    v3      VARCHAR(128) DEFAULT '',
    v4      VARCHAR(128) DEFAULT '',
    v5      VARCHAR(128) DEFAULT '',
    UNIQUE KEY uk_ptype_v (ptype, v0, v1, v2, v3, v4, v5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 说明：**角色/权限/用户由外部系统维护**（用户与角色在 Keycloak，权限点在各业务后台）。
> 它们通过外部代码把"角色→路径→方法"写入 `casbin_rule` 的 `p` 行（约定见第 6 节）。
> 网关只认 `casbin_rule`，不直连业务用户/角色表。

---

## 4. pom.xml 配置

### 4.1 在 `xxs-dependencies/pom.xml` 的 `<dependencyManagement>` 内新增

```xml
<dependency>
  <groupId>org.casbin</groupId>
  <artifactId>jcasbin</artifactId>
  <version>1.40.1</version> <!-- 登记稳定版本 -->
</dependency>
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.2.0</version>
</dependency>
```

### 4.2 `xxs-gateway/pom.xml`（在现有骨架基础上补全）

```xml
<project ...>
  <parent>
    <groupId>com.bigbear</groupId>
    <artifactId>xxs-component</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>xxs-gateway</artifactId>

  <properties>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- 必须自行导入（根与 xxs-dependencies 未管理） -->
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>4.1.0</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>2025.1.2</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <!-- 仅在需要 Nacos 等 Alibaba 组件时引入 -->
      <!--
      <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-alibaba-dependencies</artifactId>
        <version>2025.1.0.0</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      -->
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- 网关（WebFlux/Netty，非 servlet） -->
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- Casbin 核心（jcasbin 官方仅提供 FileAdapter/StringAdapter，JDBC 需自实现） -->
    <dependency>
      <groupId>org.casbin</groupId>
      <artifactId>jcasbin</artifactId>
    </dependency>

    <!-- 持久层 -->
    <dependency>
      <groupId>com.mybatis-flex</groupId>
      <artifactId>mybatis-flex-spring-boot4-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- 本地缓存 -->
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>

    <!-- 网关不引入任何 oauth2 / JWT 验签依赖：token 当作 opaque 串，仅查 Redis；JWT 验签在外部登录服务 -->

    <!-- 工具 -->
    <dependency>
      <groupId>cn.hutool</groupId>
      <artifactId>hutool-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>

    <!-- 测试 -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.projectreactor</groupId>
      <artifactId>reactor-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <compilerArgs>
            <arg>-parameters</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## 5. Casbin 核心组件（包 `com.bigbear.gateway.auth.casbin`）

### 5.1 存储适配 `JdbcCasbinAdapter`
- 实现 jcasbin 的 `Adapter`（建议同时实现 `BatchAdapter` 以支持批量写）。
- `loadPolicy(model)`：读取 `casbin_rule` 全量（`ptype='p'`），转换为 `casbin` 的 `Policy` 加载到 model；**走多级缓存**（先 Caffeine，再 Redis，最后查 DB 并回填两级缓存）。
- `savePolicy(model)`：全量覆盖（外部批量刷新用，低频）。
- `addPolicy` / `removePolicy`：增量写 DB + 标记缓存失效（清本地 Caffeine + Redis 对应键 + 发布 Watcher 广播）。
- 用 MyBatis-Flex Mapper（`CasbinRuleMapper`）做 CRUD，SQL 简单（按 ptype/v 条件）。
- **只处理 `p` 规则**，不读不写 `g` 规则。

### 5.2 `CasbinEnforcerFactory` / `CasbinManager`
- 构建并持有单例 `SyncedEnforcer`：`new SyncedEnforcer(modelText/model.conf, adapter)`。
- 提供 `reload()`：清缓存 + `enforcer.loadPolicy()`（供 Watcher 回调调用）。
- 缓存 key 设计：本地 Caffeine 用固定 key（如 `"casbin:policy"`）整体缓存 Policy 快照；Redis 用同 key 序列化存储（JSON）。

### 5.3 `RedisCasbinWatcher`
- 实现 jcasbin `Watcher` 接口（`setUpdateCallback` / `update`）。
- `update()`：向 Redis channel（如 `casbin:policy:update`）发布空消息。
- 订阅该 channel：收到消息后执行 `callback.run()`（即 `CasbinManager.reload()`），实现集群热更新。
- 使用 `spring-boot-starter-data-redis` 的 `RedisMessageListenerContainer` 订阅，`StringRedisTemplate` 发布。
- 触发方是**外部管理后台**改完 `casbin_rule` 后调用广播（网关侧只订阅，不主动写）。

---

## 6. 外部写入约定（网关不管理，仅定义契约）

网关不提供任何管理/转换代码。以下契约由接入的登录服务与权限管理后台遵守：

### 6.1 Redis 用户态（token → userId + roles + status）

| 项 | 约定 |
|---|---|
| Key | `{xxs.gateway.auth.token.redis-key-prefix}` + `sha256(rawToken)`<br>`rawToken` 即客户端传入的 Bearer token（由 Keycloak 颁发，网关不解析，当作不透明串）；**不存明文 token**，仅存其 sha256 |
| Value（JSON） | `{ "userId": "u1", "roles": ["admin","order:query"], "status": "valid", "exp": 1735689600, "deviceId": "web-abc", "issuedAt": 1735686000 }` |
| TTL | 建议略短于 access_token 过期时间；吊销靠 `status=revoked` 而非仅依赖 TTL |

- **单 token 踢人**：`DEL` / 置 `status=revoked` → 下次请求网关查到缺失或 `revoked` → 401。
- **按用户全端踢**：登录/令牌服务维护 `user:{userId}:tokens`（Set<sha256>），批量 `DEL` 并清集合；**如需彻底下线，须配合 Keycloak Admin API 撤销该用户会话/refresh token**（否则 refresh 后续命）。
- **登录/令牌服务流程（外部实现）**：调 Keycloak 换 `access_token`(JWT) → 在登录服务端用 Keycloak JWK 验签并解析出 `sub` / `realm_access.roles` / `jti` / `exp` → 以 `sha256(access_token)` 为 key 写入上述 Redis（并加入 `user:{userId}:tokens` 集合）。
- 网关 `TokenUserResolver` 以相同 `sha256(rawToken)` 读取；**读不到 / `status=revoked` / 已过期 → 401**。
- **网关本地缓存窗口**：若 Caffeine 缓存 `token→userInfo`，踢人后有短暂放行窗口；建议该缓存 TTL 设短（如 60s），或踢人时经 Redis Pub/Sub 清各节点 Caffeine（同 casbin watcher 思路）。

### 6.2 `casbin_rule` 的 `p` 规则写入约定

权限管理后台把"角色 → 路径 → 方法"写成一行：

```
ptype='p', v0=roleKey, v1=resourcePath, v2=httpMethod, v3..v5=''
```

例：`p, admin, /api/order/list, GET`。写完后由后台向 `casbin:policy:update` 频道 `publish` 一条消息触发网关 reload。

> 接入方可自行选择：直接写 `casbin_rule` SQL、或用 MyBatis-Flex 复用 `CasbinRuleMapper` 实体。
> 网关只消费 `casbin_rule`，不暴露写 API。

### 6.3 管理系统对接约定（多端 / 账号·角色·权限）

管理系统（后台）同时承担"账号/角色管理"与"权限点管理"两个写入面，关键是通过**角色 key 同名**契约把 Keycloak 与 casbin 接起来：

- **胶水约定（强制）**：casbin 的 `p.sub`（角色 key）**必须等于 Keycloak 的 Realm Role 名**。例如 Keycloak 有 `admin`、`order:query` 两个 realm role，则 `casbin_rule` 只能写 `p, admin, ...`、`p, order:query, ...`。这是连接"Keycloak 角色"与"casbin 权限"的唯一桥梁。
- **账号与角色分配（走 Keycloak Admin API）**：管理系统后端用 `client_credentials` 拿管理员令牌，调用 Keycloak **Admin REST API**（`keycloak-admin-client` 或原生 REST）增删用户、创建/分配/回收 realm role。**这一步不碰 casbin**，用户→角色映射的真相在 Keycloak。
- **权限点管理（走 casbin_rule 写入 + Watcher 热更新）**：管理系统编辑"角色 → 资源 → 方法"时，删除该角色旧 `p` 规则 → 写入新 `p` 规则到 `casbin_rule` → 向 `casbin:policy:update` 频道 publish。网关 `RedisCasbinWatcher` 订阅后 `reload()`，角色权限变更即时生效（无需用户重登录）。
- **App 自注册默认角色**：App 用户自注册后挂默认 realm role `app_user`；业务侧给 `app_user` 配 `p` 规则即可获得基础权限。
- **角色权限变更即时生效机制说明**：① 角色→资源权限点的变更 = 改 casbin `p` 规则（经 Watcher 热更新）；② 用户→角色的变更 = Keycloak Admin API 改 realm role 映射，**网关不实时拉**，而是读 Redis 里的 `roles[]`；要即时生效，靠短访问令牌 + `ver`（令牌版本）机制（改角色时 `user:{uid}:ver++`，网关校验 token.ver ≠ redis.ver → 401 强制重登录）。

### 6.4 App 手机号注册/登录（Keycloak 自定义 Authenticator SPI，路线 A）

App 端用户**自己用手机号 + 短信验证码注册/登录**。Keycloak 原生只认用户名/邮箱+密码，需通过**自定义 Authentication Flow + Authenticator（SPI）**实现"手机号 + 短信验证码"；可基于社区开源 `keycloak-phone-authenticator` 类项目改造。

- **注册流**：填手机号 → 调短信网关（阿里云/腾讯云等）发验证码 → 校验 → 创建用户（手机号作 username 或 user attribute）→ 赋默认角色 `app_user`。
- **登录流**：填手机号 → 发验证码 → 校验通过 → Keycloak 正常发 `access_token`。
- **路线 A（经登录/令牌服务中转，本方案采用）**：App **不直接连 Keycloak**，把"手机号 + 验证码"给自有**登录/令牌服务**；服务用 Keycloak 自定义 Direct Grant（手机号当用户名、验证码当密码）换 `access_token`(JWT) → 服务用 Keycloak JWK 验签解析出 `userId`/`realm_access.roles`/`jti`/`exp` → 以 `sha256(access_token)` 为 key 写 Redis（并加入 `user:{userId}:tokens` 集合）→ 返 token 给 App。
- **token 在网关侧**：App 拿到的 `access_token` 在网关仍当 opaque 串，`sha256(token)` 查 Redis → casbin `enforce`。
- **短信工程细节（外部实现负责）**：手机号唯一性约束、验证码防刷/过期、短信成本控制。

路线 A 时序（登录/注册）：

```
App(手机号+验证码) → 登录/令牌服务
  登录/令牌服务 → Keycloak(手机号 Authenticator)：校验验证码、换 access_token(JWT)
  Keycloak → 登录/令牌服务：access_token
  登录/令牌服务：用 Keycloak JWK 验签解析 → userId/roles
  登录/令牌服务 → Redis：写 token:{sha256}→userId+roles+status；user:{uid}:tokens 加成员
  登录/令牌服务 → App：access_token
后续：App 请求带 token → 网关 sha256 查 Redis → casbin enforce
```

---

## 7. 网关鉴权过滤器（包 `com.bigbear.gateway.auth.filter`）

### 7.1 `CasbinAuthorizationGlobalFilter implements GlobalFilter, Ordered`
- `getOrder()` 返回 **-100**（早于业务路由过滤）。
- 流程：
  1. **白名单**：`OPTIONS` 请求、`/actuator/**`、配置的白名单前缀（如 `/auth/login`、`/swagger-ui/**`）直接 `chain.filter(exchange)` 放行。
    2. **解析用户**：`AuthorizationResolver` 从 `Authorization: Bearer <token>` 取出**原始 token 串**（不验签、不解析）→ `TokenUserResolver` 以 `sha256(rawToken)` 为 key 查 Redis 得 `TokenUserInfo(userId, roles, status)`。
     - 无 token / Redis 无该 token / `status=revoked` / 已过期 → 返回 **401**（`ServerHttpResponse` 写 JSON + `setStatusCode(UNAUTHORIZED)` 并 `return response.setComplete()` 短路）。
  3. **规范化请求**：取 `request.getPath().value()`（去 query）、`request.getMethod().name()`（大写）。
  4. **鉴权**（对每个角色逐一 enforce，命中任一即通过）：
     ```java
     Mono<Boolean> allowed = Mono.fromCallable(() -> {
         SyncedEnforcer enforcer = casbinManager.getEnforcer();
         return userInfo.roles().stream()
                 .anyMatch(role -> enforcer.enforce(role, path, method));
     }).subscribeOn(Schedulers.boundedElastic());
     ```
  5. `allowed` 为 false → 返回 **403** 并短路；true → `chain.filter(exchange)` 放行（把 `userId`、角色放入 `exchange.getAttributes()` 或下游请求头透传）。

### 7.2 解析器 / 扩展点（包 `com.bigbear.gateway.auth.resolver`）
- `AuthorizationResolver` 接口：`resolve(exchange) -> Mono<RawTokenContext>`（从 `Authorization: Bearer <token>` 取出**原始 token 串**，不验签不解析）。
  - 默认实现 `HeaderTokenAuthorizationResolver`：读 `Authorization` 头取 Bearer token。
- `TokenUserResolver` 接口：`resolve(rawToken) -> Mono<TokenUserInfo>`（以 `sha256(rawToken)` 查 Redis 得 `userId+roles+status`；`status≠valid` 或缺失 → 抛未认证）。
  - 默认实现 `RedisTokenUserResolver`：`StringRedisTemplate` 按 `{prefix}{sha256(rawToken)}` 取 JSON，反序列化 `TokenUserInfo`（含 `status` 字段校验）。
- **删除** `TokenParser` / `KeycloakJwtTokenParser`（网关不验签 JWT）。JWT 验签解析由外部登录/令牌服务完成。
- 以上默认 Bean 若容器中已存在用户自定义实现则不被覆盖（SPI 扩展）。

---

## 8. 自动装配与配置（可复用 Starter）

### 8.1 `CasbinGatewayProperties`（prefix = `xxs.gateway.auth`）
- `modelLocation`：model.conf 路径，默认 `classpath:casbin/model.conf`
- `enabled`：总开关，默认 `true`
- `whitelist`：`List<String>` 白名单路径前缀（默认含 `/actuator/**`、`/auth/login` 等）
- `cache`：Caffeine/Redis 相关配置（ttl、最大条数）
- `watcher.redisChannel`：广播 channel，默认 `casbin:policy:update`
- `token.redisKeyPrefix`：Redis 用户态 key 前缀，默认 `gateway:token:user:`（实际 key = `{prefix}` + `sha256(rawToken)`）
- （已移除 `oauth2.issuerUri` / `oauth2.jwkSetUri` / `token.redisKeyClaim`：网关不再需要 Keycloak 地址；Keycloak JWK 验签只在外部登录/令牌服务进行）

### 8.2 `CasbinGatewayAutoConfiguration`
- `@AutoConfiguration` + `@EnableConfigurationProperties(CasbinGatewayProperties.class)`
- 条件装配：`@ConditionalOnProperty(prefix="xxs.gateway.auth", name="enabled", havingValue="true", matchIfMissing=true)`
- 依次注册：`CasbinRuleMapper`（MyBatis-Flex）、`JdbcCasbinAdapter`、`RedisCasbinWatcher`、`CasbinManager`（构建 `SyncedEnforcer` 并挂载 watcher）、`RedisTokenUserResolver`、`CasbinAuthorizationGlobalFilter`、`AuthorizationResolver`/`TokenUserResolver`（默认实现；若容器已存在用户自定义 Bean 则不覆盖）。**不再注册 `NimbusReactiveJwtDecoder` / `TokenParser`**（网关不验签 JWT）。
- `model.conf` 放在 `src/main/resources/casbin/model.conf`。

### 8.3 SPI 注册
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  写入 `com.bigbear.gateway.auth.config.CasbinGatewayAutoConfiguration`

---

## 9. 目录结构（落地对照）

```
xxs-gateway/
├─ pom.xml
├─ src/main/java/com/bigbear/gateway/auth/
│  ├─ config/
│  │   ├─ CasbinGatewayProperties.java
│  │   └─ CasbinGatewayAutoConfiguration.java
│  ├─ filter/
│  │   └─ CasbinAuthorizationGlobalFilter.java
│  ├─ casbin/
│  │   ├─ cache/        Caffeine/Redis 多级缓存工具
│  │   ├─ model/        CasbinRule 实体、CasbinRuleMapper
│  │   ├─ adapter/      JdbcCasbinAdapter（实现 Adapter/BatchAdapter，仅 p 规则）
│  │   ├─ watcher/      RedisCasbinWatcher
│  │   └─ manager/      CasbinManager / CasbinEnforcerFactory
│  ├─ resolver/
│  │   ├─ AuthorizationResolver.java
│  │   ├─ HeaderTokenAuthorizationResolver.java
│  │   ├─ TokenUserResolver.java
│  │   ├─ RedisTokenUserResolver.java      (默认：按 sha256(rawToken) 查 Redis 得 userId+roles+status)
│  │   └─ model/        TokenUserInfo(userId, roles[]) 等
│  └─ (无 controller / service / domain / converter —— 网关不管理)
├─ src/main/resources/
│  ├─ casbin/model.conf
│  ├─ schema.sql
│  └─ META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
└─ src/test/java/...   (WebTestClient 集成测试)
```

---

## 10. 测试与验收标准

1. **构建**：`mvn -pl xxs-component/xxs-gateway -am clean package` 通过，无 servlet/web 冲突。
2. **集成测试**（用 `WebTestClient` + 内存/测试库 + 测试 Redis）：
   - 预置 `casbin_rule`：`p, admin, /api/test, GET`（仅 p 规则，无 g）。
   - 预置 Redis：`gateway:token:user:{sha256(token)}` → `{"userId":"u1","roles":["admin"],"status":"valid"}`（测试用固定 token 串，key 取其 sha256）。
   - 请求带合法 token（`sha256` 命中 Redis）访问 `/api/test` GET → 200。
   - 角色为 `user`（无该路径权限）→ 403。
   - 无 token / Redis 无该 token（`sha256` 未命中） / `status=revoked` / 已过期 → 401。
   - 白名单路径 → 直接放行。
3. **不阻塞 event loop**：`enforce`、Redis 取用户必须在 `boundedElastic` 调度；测试可用 `StepVerifier` 验证 Mono 行为；用 BlockHound（若引入）确保无同步阻塞。
4. **热更新**：测试环境中向 `casbin_rule` 写入新 `p` 规则并向 `casbin:policy:update` 频道 publish，验证网关节点经 Watcher 重载后新策略生效。
5. **契约不泄漏内部**：测试与文档示例**绝不出现** `ptype/v0/v1` 等 Casbin 内部字段；Redis 用户态只暴露 `userId` 与 `roles`。
6. **缓存一致性**：外部写 `casbin_rule` 并广播后，本地 Caffeine 与 Redis 策略缓存失效，下次请求回源 DB 并回填。

---

## 11. 默认决策与待确认项（实现时按以下默认值，必要时向用户确认）

- 用户/角色由 **Keycloak** 托管；网关不建用户/角色/权限元数据表，扁平 `casbin_rule` 仅存 `p` 规则即足够。
- `userId` 取自 Redis 用户态；角色集合来自 Redis 用户态的 `roles`，**不**经 casbin `g` 规则。
- 路径匹配默认**精确匹配**（按 model.conf 的 `r.obj == p.obj`）；如需 Ant 风格前缀匹配，再引入 `keyMatch2` 等 Casbin 内置函数并在 matcher 中使用（此时 `p.obj` 可写 `/api/order/**`）。
- **令牌模式：reference token（opaque），非 JWT 验签**：网关不依赖 Keycloak 运行时，仅以 `sha256(token)` 查 Redis 用户态；JWT 验签/声明解析在外部登录/令牌服务完成。
- **角色 key（casbin `p.sub`）必须等于 Keycloak Realm Role 名**（胶水约定，保证角色在 Keycloak 与 casbin 间对齐）。
- **踢人粒度**：单 token（删/标记 Redis + `status=revoked`）+ 按用户全端（`user:{userId}:tokens` 集合批量删，建议配合 Keycloak 会话撤销以彻底下线）。
- **App 手机号注册/登录走路线 A**：App 不直接连 Keycloak，经登录/令牌服务中转（手机号+验证码 → 服务换 token → 验签解析 → 写 Redis），见第 6.4 节。
- **网关本地缓存窗口**：若 Caffeine 缓存 `token→userInfo`，踢人后有短暂放行窗口；建议该缓存 TTL 设短（如 60s），或踢人时经 Redis Pub/Sub 清各节点 Caffeine（同 casbin watcher 思路）。
- Redis 用户态 key = `{prefix}` + `sha256(rawToken)`（不再使用 JWT 的 `jti`/`sub`，网关不解析 JWT）。

---

## 12. 给实现端 AI 的明确要求

- 不要重新设计存储模型，严格使用 `casbin_rule` 扁平表（方案 A），**只写 `p` 规则，不处理 `g`**。
- **不要**实现任何角色/权限/用户管理接口（`CasbinPolicyService` / `CasbinPolicyController` / 业务 DTO / `CasbinPolicyConverter` 一律不要）。
- **不要**引入 `spring-boot-starter-oauth2-resource-server` 与 `spring-security-oauth2-jose` 等任何 OAuth2 / 安全过滤器链；网关不验签 JWT，token 当作 opaque 串，仅以 `sha256(token)` 查 Redis。JWT 验签在外部登录/令牌服务完成。
- 全程响应式，任何阻塞（Redis 取用户、DB、enforce）必须 `subscribeOn(Schedulers.boundedElastic())`。
- 实现完成后，附一段最简 `application.yml` 示例与"如何接入业务系统"的说明：
  - 引入本模块依赖 + 配置 `xxs.gateway.auth.*`（token.redis-key-prefix、whitelist、cache、watcher.redis-channel）。
  - 由登录/令牌服务（对接 Keycloak）负责：换 token → 验签解析 JWT 取 userId/roles → 以 `sha256(token)` 为 key 把 `userId+roles+status` 写入 Redis（并加入 `user:{userId}:tokens` 集合），TTL 对齐 token。
  - 由权限管理后台负责：把"角色→路径→方法"写入 `casbin_rule` 的 `p` 行，并 publish `casbin:policy:update` 触发网关 reload。
  - 如需自定义 token 解析/用户来源，实现 `TokenUserResolver` 扩展点替换默认 Bean（网关不提供 `TokenParser`）。
