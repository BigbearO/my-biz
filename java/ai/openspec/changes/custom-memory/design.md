# Design: 自定义 ChatMemory 分层存储架构设计（Redis List + MySQL + Mybatis-Plus）

## Context

基于 Spring AI 1.1.4 实现自定义 ChatMemory，**直接实现 `org.springframework.ai.chat.memory.ChatMemory` 接口**，整合 Redis List 和 MySQL 实现分层存储。需要仿照原生 `MessageWindowChatMemory` 的窗口逻辑，在 `ai-biz` 和 `infra` 模块下实现。

**约束条件**：
- 直接实现 `org.springframework.ai.chat.memory.ChatMemory` 接口（`add` / `get` / `clear`）。
- 适配 Spring AI 1.1.4 原生消息类型（`UserMessage`、`AssistantMessage`、`SystemMessage`）。
- 基于 Mybatis-Plus 实现数据库操作。
- 使用 `MessageHistoryEntity` 作为数据库实体，**禁止 JSON 字段**存储消息，字段结构化拆分（role / content / type）。
- Redis 使用 **List 结构**存储，**一条消息对应一个 List 元素**。
- 窗口逻辑**仅在 Redis 层**处理，数据库不分页删除、不截断。

## Goals / Non-Goals

### Goals
- 实现 `CustomChatMemory` 直接实现 `ChatMemory` 接口，自行管理窗口逻辑。
- Redis List 用于短期上下文窗口（滑动窗口裁剪：RPUSH + LTRIM）。
- MySQL 用于长期历史全量存储（只 INSERT，不 UPDATE/DELETE）。
- 读取：优先 Redis，无数据查 MySQL 兜底，并回填 Redis。
- 写入：先落库 MySQL，再操作 Redis。
- 清空记忆：只删 Redis，保留 MySQL 全量历史。
- 数据转换：数据库实体 ↔ Spring AI Message 双向转换；Redis List 单元素轻量化存储。

### Non-Goals
- 不实现 `ChatMemoryRepository` 接口（直接实现 `ChatMemory`）。
- 不使用 JSON 字段存储消息整体。
- 不修改已有数据库表结构（复用 `message_history` 表）。
- 不实现消息修改和删除功能（MySQL 只追加）。
- 不实现分布式锁（假设单实例写入或业务层保证顺序）。

## Decisions

### Decision 1: 直接实现 ChatMemory 接口

Spring AI 1.1.4 的 `ChatMemory` 接口定义：
```java
public interface ChatMemory {
    void add(String conversationId, List<Message> messages);
    List<Message> get(String conversationId);
    void clear(String conversationId);
}
```

我们将实现一个核心类 `CustomChatMemory` 直接实现该接口，内部协调两个 Store：
- `RedisChatMemoryStore`：Redis List 操作封装，负责短期窗口存储。
- `MySQLChatMemoryStore`：MySQL + Mybatis-Plus 操作封装，负责长期归档存储。

`CustomChatMemory` 自行管理滑动窗口逻辑（仿照 `MessageWindowChatMemory`），不依赖框架的 `MessageWindowChatMemory`。

### Decision 2: CustomChatMemory 整合逻辑

```java
@Component
@RequiredArgsConstructor
public class CustomChatMemory implements ChatMemory {

    private final RedisChatMemoryStore redisStore;
    private final MySQLChatMemoryStore mysqlStore;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    /**
     * 读取：优先 Redis，无数据查 MySQL 兜底，并回填 Redis
     */
    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = redisStore.getMessages(conversationId);
        if (messages.isEmpty()) {
            messages = mysqlStore.findRecentMessages(conversationId, maxMessages);
            if (!messages.isEmpty()) {
                redisStore.saveMessages(conversationId, messages); // 回填 Redis
            }
        }
        return messages;
    }

    /**
     * 写入：先落库 MySQL，再操作 Redis
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || CollectionUtils.isEmpty(messages)) {
            return;
        }
        for (Message message : messages) {
            if (message == null) continue;
            mysqlStore.saveMessage(conversationId, message);
            redisStore.addMessage(conversationId, message, maxMessages);
        }
    }

    /**
     * 清空：只删 Redis，保留 MySQL
     */
    @Override
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        redisStore.deleteMessages(conversationId);
    }
}
```

### Decision 3: Redis List 一条消息一个元素

Redis 使用 List 结构，**一条消息对应一个 List 元素**，不将多条消息打包成一个 JSON 字符串。

```
Key: chat:memory:{conversationId}
Type: List

Element 0: {"role":"user","content":"你好"}
Element 1: {"role":"assistant","content":"您好！"}
Element 2: {"role":"user","content":"今天天气如何"}
Element 3: {"role":"assistant","content":"今天晴天..."}
```

操作命令：
- `RPUSH key element`：追加单条消息
- `LTRIM key -N -1`：裁剪窗口，保留最近 N 条
- `LRANGE key 0 -1`：读取全部消息
- `DEL key`：清空会话

### Decision 4: Redis List 轻量化存储

不在 Redis 中存储完整的 Message JSON（包含 `@class` 等元数据），而是存储轻量化 `SimpleMessage`：

```java
public class SimpleMessage implements Serializable {
    private String role;
    private String content;
}
```

Redis List 元素示例：
```
{"role": "user", "content": "你好"}
{"role": "assistant", "content": "您好！"}
```

### Decision 5: 数据转换规则

**Redis ↔ Spring AI Message**：
```java
private Message toMessage(SimpleMessage simple) {
    return switch (simple.getRole().toLowerCase()) {
        case "user" -> new UserMessage(simple.getContent());
        case "assistant" -> new AssistantMessage(simple.getContent());
        case "system" -> new SystemMessage(simple.getContent());
        default -> { log.warn("Unknown role: {}", simple.getRole()); yield null; }
    };
}
```

**MySQL Entity ↔ Spring AI Message**：
```java
private Message toMessage(MessageHistoryEntity entity) {
    return switch (entity.getType()) {
        case 1 -> new UserMessage(entity.getContent());
        case 2 -> new AssistantMessage(entity.getContent());
        case 0 -> new SystemMessage(entity.getContent());
        default -> { log.warn("Unknown type: {}", entity.getType()); yield null; }
    };
}
```

| MessageHistoryEntity | Spring AI Message | 说明 |
|---------------------|-------------------|------|
| type = 1, role = "user" | UserMessage | 用户消息 |
| type = 2, role = "assistant" | AssistantMessage | AI 回复 |
| type = 0, role = "system" | SystemMessage | 系统消息 |

### Decision 6: Redis 滑动窗口实现

```java
public void addMessage(String conversationId, Message message, int maxSize) {
    String key = buildKey(conversationId);
    String json = serialize(toSimpleMessage(message));

    // RPUSH: 右侧追加新消息（一条消息一个元素）
    redisTemplate.opsForList().rightPush(key, json);

    // LTRIM: 裁剪窗口，只保留最新 maxSize 条
    redisTemplate.opsForList().trim(key, -maxSize, -1);

    // EXPIRE: 续期 TTL
    redisTemplate.expire(key, ttl);
}
```

### Decision 7: MySQL 兜底查询

```java
public List<Message> findRecentMessages(String conversationId, int limit) {
    LambdaQueryWrapper<MessageHistoryEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(MessageHistoryEntity::getChatId, conversationId)
           .orderByAsc(MessageHistoryEntity::getCreateTime)
           .last("LIMIT " + limit);

    List<MessageHistoryEntity> entities = messageHistoryMapper.selectList(wrapper);
    return entities.stream()
            .map(this::toMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

### Decision 8: 异常处理与降级

- **Redis 读取失败**：返回空列表，记录错误日志，触发 MySQL 兜底查询。
- **Redis 写入失败**：MySQL 已写入成功，记录错误日志，不抛异常，下次读取可从 MySQL 恢复。
- **MySQL 写入失败**：抛异常，Redis 不执行（保证 MySQL 优先落库）。
- **反序列化失败**：跳过该消息，记录警告日志，继续处理剩余消息。

## Components

### 1. SimpleMessage（DTO）

```java
package com.bigbear.ai.biz.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 14399L;

    private String role;
    private String content;

    public SimpleMessage() {}

    public SimpleMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
```

### 2. RedisChatMemoryStore

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.chat.memory.key-prefix:chat:memory:}")
    private String keyPrefix;

    @Value("${spring.ai.chat.memory.redis-ttl-days:7}")
    private long ttlDays;

    /**
     * 获取消息列表（LRANGE 0 -1）
     */
    public List<Message> getMessages(String conversationId) {
        String key = buildKey(conversationId);
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (CollectionUtils.isEmpty(jsonList)) {
                return new ArrayList<>();
            }
            return jsonList.stream()
                    .map(this::deserialize)
                    .filter(Objects::nonNull)
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis getMessages 失败，key: {}", key, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存消息列表（DEL + RPUSHALL + EXPIRE）
     */
    public void saveMessages(String conversationId, List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) return;
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
            List<String> jsonList = messages.stream()
                    .map(this::toSimpleMessage)
                    .map(this::serialize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!jsonList.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(key, jsonList);
                redisTemplate.expire(key, Duration.ofDays(ttlDays));
            }
        } catch (Exception e) {
            log.error("Redis saveMessages 失败，key: {}", key, e);
        }
    }

    /**
     * 追加单条消息（RPUSH + LTRIM + EXPIRE）
     */
    public void addMessage(String conversationId, Message message, int maxSize) {
        if (message == null) return;
        String key = buildKey(conversationId);
        try {
            String json = serialize(toSimpleMessage(message));
            if (json == null) return;
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
            log.debug("Redis 追加消息成功，conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Redis addMessage 失败，key: {}", key, e);
        }
    }

    /**
     * 删除会话消息（DEL）
     */
    public void deleteMessages(String conversationId) {
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
            log.debug("Redis 删除会话成功，conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Redis deleteMessages 失败，key: {}", key, e);
        }
    }

    private String buildKey(String conversationId) {
        return keyPrefix + conversationId;
    }

    private SimpleMessage toSimpleMessage(Message message) {
        String role = message.getMessageType().name().toLowerCase();
        return new SimpleMessage(role, message.getText());
    }

    private Message toMessage(SimpleMessage simple) {
        if (simple == null || simple.getRole() == null) return null;
        return switch (simple.getRole().toLowerCase()) {
            case "user" -> new UserMessage(simple.getContent());
            case "assistant" -> new AssistantMessage(simple.getContent());
            case "system" -> new SystemMessage(simple.getContent());
            default -> { log.warn("Unknown role: {}", simple.getRole()); yield null; }
        };
    }

    private String serialize(SimpleMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Serialize SimpleMessage 失败", e);
            return null;
        }
    }

    private SimpleMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json, SimpleMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize SimpleMessage 失败，json: {}", json, e);
            return null;
        }
    }
}
```

### 3. MySQLChatMemoryStore

```java
@Repository
@RequiredArgsConstructor
@Slf4j
public class MySQLChatMemoryStore {

    private final MessageHistoryMapper messageHistoryMapper;

    @Transactional
    public void saveMessage(String conversationId, Message message) {
        if (message == null) return;
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getText());
        entity.setType(detectMessageType(message));
        entity.setRole(detectRole(message));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        try {
            messageHistoryMapper.insert(entity);
            log.debug("MySQL 保存消息成功，conversationId={}，role={}", conversationId, entity.getRole());
        } catch (Exception e) {
            log.error("MySQL saveMessage 失败，conversationId={}", conversationId, e);
            throw e;
        }
    }

    public List<Message> findRecentMessages(String conversationId, int limit) {
        LambdaQueryWrapper<MessageHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageHistoryEntity::getChatId, conversationId)
               .orderByAsc(MessageHistoryEntity::getCreateTime)
               .last("LIMIT " + limit);
        try {
            return messageHistoryMapper.selectList(wrapper)
                    .stream()
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MySQL findRecentMessages 失败，conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    private int detectMessageType(Message message) {
        return switch (message.getMessageType()) {
            case USER -> 1;
            case ASSISTANT -> 2;
            case SYSTEM -> 0;
            default -> 3;
        };
    }

    private String detectRole(Message message) {
        return message.getMessageType().name().toLowerCase();
    }

    private Message toMessage(MessageHistoryEntity entity) {
        if (entity == null) return null;
        return switch (entity.getType()) {
            case 1 -> new UserMessage(entity.getContent());
            case 2 -> new AssistantMessage(entity.getContent());
            case 0 -> new SystemMessage(entity.getContent());
            default -> { log.warn("Unknown type: {}, skipping", entity.getType()); yield null; }
        };
    }
}
```

### 4. CustomChatMemory

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomChatMemory implements ChatMemory {

    private final RedisChatMemoryStore redisStore;
    private final MySQLChatMemoryStore mysqlStore;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = redisStore.getMessages(conversationId);
        if (messages.isEmpty()) {
            log.debug("Redis 空，fallback 到 MySQL，conversationId={}", conversationId);
            messages = mysqlStore.findRecentMessages(conversationId, maxMessages);
            if (!messages.isEmpty()) {
                log.debug("MySQL 查询到 {} 条消息，回填 Redis，conversationId={}", messages.size(), conversationId);
                redisStore.saveMessages(conversationId, messages);
            }
        }
        return messages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.isBlank() || CollectionUtils.isEmpty(messages)) {
            return;
        }
        for (Message message : messages) {
            if (message == null) continue;
            mysqlStore.saveMessage(conversationId, message);
            redisStore.addMessage(conversationId, message, maxMessages);
        }
    }

    @Override
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        redisStore.deleteMessages(conversationId);
    }
}
```

### 5. ChatMemoryConfig

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ChatMemoryConfig {

    private final CustomChatMemory customChatMemory;

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(customChatMemory).build();
    }
}
```

## Redis vs MySQL Responsibilities

| 职责 | Redis List | MySQL |
|------|-----------|-------|
| 滑动窗口 | ✅ LTRIM 实现 | ❌ 不处理 |
| 短期上下文 | ✅ | ❌ |
| 全量历史 | ❌ 不存储 | ✅ INSERT 全量 |
| 兜底查询 | ❌ | ✅ |
| 会话清空 | ✅ DEL key | ❌ 保留 |
| 结构化字段 | ❌ 轻量化 JSON | ✅ role/content/type 拆分 |

## Configuration

```yaml
spring:
  ai:
    chat:
      memory:
        max-messages: 10        # 滑动窗口大小（保留最新 N 条消息）
        redis-ttl-days: 7       # Redis Key 过期时间（天）
        key-prefix: "chat:memory:"  # Redis Key 前缀
```

## Redis Commands Reference

```bash
# 查看 List 长度
LLEN chat:memory:session_001

# 查看全部消息
LRANGE chat:memory:session_001 0 -1

# 查看最近 5 条
LRANGE chat:memory:session_001 -5 -1

# 保留最近 10 条（滑动窗口）
LTRIM chat:memory:session_001 -10 -1

# 查看 Key 过期时间
TTL chat:memory:session_001

# 删除会话（清空记忆）
DEL chat:memory:session_001
```

## Open Questions

1. **并发写入**：同一 conversationId 并发 add 时，Redis 端 RPUSH + LTRIM 是原子操作，但 MySQL 和 Redis 之间非原子。当前假设业务层保证顺序，或后续引入分布式锁。
2. **TTL 策略**：当前每次写入都重置 TTL，是否需要根据会话活跃度动态调整？
3. **MySQL 数据量增长**：`message_history` 表随时间增长，是否需要归档策略？
