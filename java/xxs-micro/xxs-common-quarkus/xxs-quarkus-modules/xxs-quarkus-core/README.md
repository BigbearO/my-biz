# xxs-quarkus-core — 统一身份上下文与跨语言鉴权透传

提供业务侧**透明获取当前登录用户**的能力，配合 `xxs-quarkus-grpc` 实现 gRPC 链路四头零解析透传。

> 设计原则：Token 仅在边界（Spring Cloud Gateway）校验一次，校验通过后在 `X-User-Id` / `X-User-Info`（HTTP 头）注入身份，下游只读头、不解析 Token。

## 跨语言四头契约

| 头 | 常量（`CommonConstants`） | 用途 |
|---|---|---|
| `X-User-Id` | `USER_ID` | 用户唯一 ID（网关取自 Redis 用户态） |
| `X-User-Info` | `USER_INFO` | 完整用户信息 JSON（`userId/roles/username/tenantId/deviceId`） |
| `Authorization` | `AUTH` | 原始 Bearer token（透传给需调下游三方的服务） |
| `X-Client-Id` | `CLIENT_ID` | 调用方客户端标识（服务间调用时由发起方写入） |

`userId` 为空即匿名，对应网关白名单（`/actuator/**`、`/health` 等）。

## 包结构

- `core.model.LoginUser` — 跨语言统一身份载体（与 Spring / Go 侧字段逐字对齐）
- `core.util.HeaderUtils` — 从 REST 请求头取身份 + 静态 JSON 工具
- `core.context.UserContext` — 统一身份上下文，业务侧透明取用户
- `core.filter.AuthContainer{Request,Response}Filter` — REST 入站注入 / 出站清空 `UserContext`

## 业务侧获取当前用户

```java
import com.bigbear.quarkus.core.context.UserContext;
import com.bigbear.quarkus.core.model.LoginUser;

// 透明获取；无登录信息返回 null（匿名）
LoginUser user = UserContext.current();
if (UserContext.isAnonymous()) {
    // 匿名：网关白名单接口，按匿名逻辑处理
}
String userId  = user != null ? user.getUserId()  : null;
String username = user != null ? user.getUsername() : null;
List<String> roles = user != null ? user.getRoles() : List.of();
```

## REST 入站（自动）

`AuthContainerRequestFilter` / `AuthContainerResponseFilter` 通过 `@Provider` 自动注册：

- 入站：读 `X-User-Id` / `X-User-Info` → 注入 `UserContext`；
- 出站：请求结束清空 `UserContext`，避免 ThreadLocal 跨请求泄漏。

```java
@Path("/order")
public class OrderResource {

    @GET
    public List<Order> list() {
        // 直接拿当前用户，无需关心头
        LoginUser u = UserContext.current();
        // ...
    }
}
```

## HeaderUtils 静态工具

```java
import com.bigbear.quarkus.core.util.HeaderUtils;

LoginUser u = HeaderUtils.parseLoginUser(jsonString); // JSON -> LoginUser（失败返回 null）
String json = HeaderUtils.toJson(u);                  // LoginUser -> JSON
MyReq  req  = HeaderUtils.toBean(jsonString, MyReq.class);
```

## 测试 / 非 HTTP 上下文手动注入

```java
UserContext.set(new LoginUser("u1", List.of("biz.admin"), "alice", null, null));
try {
    // ... 业务逻辑
} finally {
    UserContext.clear(); // 必须清理
}
// 或用 withUser 暂存原值
LoginUser prev = UserContext.withUser(guest);
UserContext.set(prev);
```

## LoginUser 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | `String` | 用户唯一 ID（必填，空即匿名） |
| `roles` | `List<String>` | 角色 key（与 Keycloak realm role 同名） |
| `username` | `String` | 用户名 |
| `tenantId` | `String` | 多租户预留字段（本期单租户，不参与鉴权判定） |
| `deviceId` | `String` | 设备标识 |
