# xxs-spring-auth — Spring 统一身份工具（不含 gRPC）

提供跨语言鉴权透传的**基础工具**：身份载体、头常量、JSON 工具、入站读取工具、统一上下文。gRPC 拦截器在 `xxs-spring-grpc` 模块。

## Maven 引入

```xml
<dependency>
  <groupId>com.bigbear</groupId>
  <artifactId>xxs-spring-auth</artifactId>
</dependency>
```

（版本由 `xxs-spring-bom` 统一管理，无需写版本号。）

## 跨语言四头契约（`AuthHeaders`）

| 头 | `AuthHeaders` 常量 | 用途 |
|---|---|---|
| `X-User-Id` | `HEADER_USER_ID` | 用户唯一 ID |
| `X-User-Info` | `HEADER_USER_INFO` | 完整用户信息 JSON |
| `Authorization` | `HEADER_AUTHORIZATION` | 原始 Bearer token（透传） |
| `X-Client-Id` | `HEADER_CLIENT_ID` | 调用方客户端标识 |

## 业务侧获取当前用户

```java
import com.bigbear.spring.auth.UserContext;
import com.bigbear.spring.auth.LoginUser;

LoginUser user = UserContext.current();      // null = 匿名
if (UserContext.isAnonymous()) { /* 匿名逻辑 */ }

String uid = user != null ? user.userId() : null;
List<String> roles = user != null ? user.roles() : List.of();
```

`LoginUser` 为 `record`，字段：`userId, roles, username, tenantId, deviceId`（多租户 `tenantId` 本期单租户，不参与鉴权）。

## 入站读取工具 `RequestUserUtils`

用于在过滤器 / 控制器里从请求头取身份（零解析）：

```java
import com.bigbear.spring.auth.RequestUserUtils;

// Servlet（WebMvc）
LoginUser u = RequestUserUtils.from((HttpServletRequest) request);

// WebFlux（响应式）
LoginUser u = RequestUserUtils.from(serverHttpRequest);

// 通用 Map（如网关转发后的头集合）
LoginUser u = RequestUserUtils.from(headersMap);
```

## 手动注入（测试 / 非 HTTP 上下文）

```java
UserContext.withUser(new LoginUser("u1", List.of("biz.admin"), "alice", null, null));
try {
    // 业务逻辑
} finally {
    UserContext.clearAll(); // 清理 userId + 暂存的 auth/clientId
}
```

## 其他工具

```java
import com.bigbear.spring.auth.JsonUtils;

LoginUser u = JsonUtils.toBean(jsonString, LoginUser.class); // 失败返回 null
String json = JsonUtils.toJson(u);
```

## 与 xxs-spring-grpc 的关系

- `xxs-spring-auth`：**基础工具**，被所有 Spring 业务模块依赖；
- `xxs-spring-grpc`：依赖本模块，提供 gRPC 服务端/客户端拦截器与 REST 过滤器，完成链路透传并写入/读取 `UserContext`。
