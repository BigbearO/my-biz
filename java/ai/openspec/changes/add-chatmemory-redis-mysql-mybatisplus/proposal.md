# Change: 实现 ChatMemory 分层存储（Redis List + MySQL + Mybatis-Plus）

## Why
当前系统需要实现一个自定义 ChatMemory 方案，整合 Redis List 和 MySQL 实现分层存储：
1. **Redis List**：作为短期上下文窗口，提供高效的滑动窗口裁剪能力（RPUSH/LTRIM）
2. **MySQL**：作为长期历史存储，全量持久化消息记录
3. **Spring AI 1.1.4**：适配原生 MessageWindowChatMemory 窗口逻辑

核心设计原则：
- 读取优先查 Redis，无数据再查 MySQL 兜底
- 写入先落库 MySQL，再操作 Redis
- 窗口逻辑仅在 Redis 层处理，数据库不分页删除

## What Changes
- **ADDED** 新增 `RedisChatMemoryRepository`：基于 Redis List 实现 ChatMemoryRepository
  - 使用 RPUSH 追加消息，LTRIM 实现滑动窗口裁剪
  - 支持 LRANGE 按需读取消息
  - 配置可动态设置的窗口大小 N
  - 设置 key 过期时间，会话隔离
- **ADDED** 新增 `MySQLChatMemoryRepository`：基于 Mybatis-Plus 实现 ChatMemoryRepository
  - 使用 MessageHistoryEntity 结构化存储（role + content）
  - 按 conversationId + 创建时间正序查询最新 N 条
  - 全量追加存储，只新增、不修改、不删除
- **ADDED** 新增 `HybridChatMemoryService`：分层存储服务
  - 读取：优先 Redis List → MySQL 兜底
  - 写入：先落库 MySQL → 再操作 Redis
  - 清空记忆：只删 Redis，保留 MySQL 全量历史
- **ADDED** 新增配置类 `ChatMemoryConfig`：Spring AI 1.1.4 配置
- **ADDED** 新增 Mybatis-Plus Mapper 和 Entity 相关文件

## Impact
- **Affected specs**: ai-biz（对话记忆管理）
- **Affected code**:
  - `ai-biz/infra/manager/` 新增 RedisChatMemoryRepository
  - `ai-biz/infra/repository/` 新增 MySQLChatMemoryRepository
  - `ai-biz/service/` 新增 HybridChatMemoryService
  - `ai-biz/common/config/` 新增 ChatMemoryConfig
  - `ai-biz/domain/entity/` 复用 MessageHistoryEntity
- **Dependencies**:
  - Spring AI 1.1.4（已存在）
  - Redis（已存在，使用 List 数据结构）
  - MySQL + Mybatis-Plus（已存在）
- **Breaking Changes**: 无

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Spring AI 架构                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                       MessageChatMemoryAdvisor                               │
│                                    ↓                                         │
│                    MessageWindowChatMemory (滑动窗口)                         │
│                                    ↓                                         │
│                      HybridChatMemoryService                                 │
│                      (分层存储读写逻辑)                                       │
│                      /                    \                                 │
│                     ↓                      ↓                                 │
│    RedisChatMemoryRepository      MySQLChatMemoryRepository                 │
│    (Redis List - 滑动窗口)         (MySQL - 全量历史)                         │
│    Key: chat:memory:{id}          Table: memory                             │
│    Type: List                      MessageHistoryEntity                     │
│    Elements: role + content       字段: id, chat_id, content, type,        │
│    (轻量化 JSON)                    role, create_time                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Storage Strategy

| 存储层 | 存储内容 | 数据结构 | 写入方式 | 查询方式 | 用途 |
|--------|---------|---------|---------|---------|------|
| **Redis List** | 滑动窗口消息 | List | RPUSH + LTRIM | LRANGE | LLM 快速读取上下文 |
| **MySQL** | 全量消息历史 | 行记录 | INSERT（全量追加） | ORDER BY create_time LIMIT N | 持久化归档、兜底查询 |

## Core Rules

### 读取策略
```
1. 先查 Redis List（LRANGE key 0 -1）
2. 若 Redis 无数据 → 查 MySQL 兜底（按 conversationId + 创建时间 LIMIT N）
3. 返回消息列表
```

### 写入策略
```
1. 先落库 MySQL（INSERT 单条）
2. 再操作 Redis（RPUSH 追加 + LTRIM 裁剪窗口）
```

### 清空策略
```
1. 只删 Redis Key（DEL key）
2. MySQL 全量历史保留不删
```

### 滑动窗口逻辑
```
1. 配置文件中指定窗口大小 N
2. Redis 层使用 LTRIM key -N -1 裁剪
3. 数据库不分页删除、不截断
```

## Redis List Structure

```
Key: chat:memory:{conversationId}
Type: List
Elements (轻量化存储):
  0: {"role":"user","content":"你好"}
  1: {"role":"assistant","content":"您好！"}
  2: {"role":"user","content":"今天天气如何"}
  3: {"role":"assistant","content":"今天晴天..."}
TTL: 配置文件指定（如 7 天）

Commands:
  - RPUSH: 追加新消息
  - LTRIM -N -1: 保留最近 N 条（窗口裁剪）
  - LRANGE 0 -1: 读取全部
  - LRANGE -N -1: 读取最近 N 条
  - DEL: 清空会话
  - LLEN: 获取消息数量
```

## MySQL Table Structure

```sql
CREATE TABLE memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chat_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    content TEXT NOT NULL COMMENT '消息内容',
    type TINYINT NOT NULL COMMENT '消息类型：0=System, 1=User, 2=Assistant',
    role VARCHAR(32) NOT NULL COMMENT '角色：user, assistant, system',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_chat_id (chat_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息历史表';
```

## Configuration

```yaml
spring:
  ai:
    chat:
      memory:
        # 滑动窗口大小（保留最新 N 条消息）
        max-messages: 10
        # Redis Key 过期时间
        redis-ttl-days: 7
        # Redis Key 前缀
        key-prefix: "chat:memory:"
```

## Data Conversion

### 数据库 Entity ↔ Spring AI Message

| MessageHistoryEntity | Spring AI Message | 说明 |
|---------------------|-------------------|------|
| type = 1, role = "user" | UserMessage | 用户消息 |
| type = 2, role = "assistant" | AssistantMessage | AI 回复 |
| type = 0, role = "system" | SystemMessage | 系统消息 |

### Redis List Element ↔ Spring AI Message

Redis List 中存储轻量化 JSON：
```json
{"role": "user", "content": "消息内容"}
{"role": "assistant", "content": "AI 回复"}
```

## SimpleMessage DTO 设计

Redis List 中存储的轻量化消息结构，不使用完整的 Spring AI Message JSON：

```java
public class SimpleMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String role;
    private String content;

    public SimpleMessage() {}

    public SimpleMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

**序列化示例**：
```java
ObjectMapper mapper = new ObjectMapper();
SimpleMessage msg = new SimpleMessage("user", "你好");
String json = mapper.writeValueAsString(msg);
// {"role":"user","content":"你好"}
```

## Exception Handling

### 异常处理策略

| 场景 | 处理策略 |
|------|---------|
| Redis 写入失败 | 记录日志，MySQL 已写入不影响，下次查询从 MySQL 兜底 |
| Redis 读取失败 | 降级到 MySQL 查询 |
| MySQL 写入失败 | 抛出异常，整个事务回滚 |
| Redis Key 不存在 | 返回空列表，触发 MySQL 兜底查询 |
| 消息类型未知 | 记录警告日志，跳过该消息 |

### 异常类设计

```java
public class ChatMemoryException extends RuntimeException {
    private final ErrorCode errorCode;

    public enum ErrorCode {
        REDIS_CONNECTION_FAILED,
        MYSQL_CONNECTION_FAILED,
        SERIALIZATION_FAILED,
        DESERIALIZATION_FAILED,
        UNKNOWN_MESSAGE_TYPE
    }
}
```

## Key 管理策略

### Key 规范

| 属性 | 值 | 说明 |
|------|-----|------|
| **前缀** | `chat:memory:` | 可配置 |
| **完整格式** | `chat:memory:{conversationId}` | conversationId 由业务方传入 |
| **TTL** | 7 天（可配置） | 每次写入续期 |

### TTL 续期策略

- 每次 `addMessage()` 或 `saveAll()` 时调用 `expire(key, ttl)` 续期
- Redis 会自动在 TTL 到期后删除 Key
- 会话重新活跃时自动续期

## MessageHistoryEntity 字段映射

| Entity 字段 | 类型 | 说明 | MySQL 列 |
|------------|------|------|----------|
| id | Long | 主键 | `id` BIGINT |
| chatId | String | 会话 ID | `chat_id` VARCHAR(64) |
| content | String | 消息内容 | `content` TEXT |
| type | Integer | 消息类型 | `type` TINYINT |
| role | String | 角色 | `role` VARCHAR(32) |
| createTime | LocalDateTime | 创建时间 | `create_time` DATETIME |

**type 字段枚举值**：
- `0` = SystemMessage
- `1` = UserMessage
- `2` = AssistantMessage
- `3` = 其它（如 ToolMessage）

## References
- [Spring AI ChatMemory 官方文档](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI MessageWindowChatMemory 源码](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-core/src/main/java/org/springframework/ai/chat/memory/MessageWindowChatMemory.java)
- [Redis List 命令文档](https://redis.io/commands/?group=list)
- [Mybatis-Plus 官方文档](https://baomidou.com/)
