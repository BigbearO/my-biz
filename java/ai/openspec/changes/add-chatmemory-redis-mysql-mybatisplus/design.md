# Design: ChatMemory 分层存储架构设计（Redis List + MySQL + Mybatis-Plus）

## Context

基于 Spring AI 1.1.4 实现自定义 ChatMemory，整合 Redis List 和 MySQL 实现分层存储。需要仿照原生 MessageWindowChatMemory 的窗口逻辑，在 ai-biz 和 infra 模块下实现。

**约束条件**：
- 适配 Spring AI 1.1.4 原生消息类型
- 基于 Mybatis-Plus 实现数据库操作
- 使用 MessageHistoryEntity 作为数据库实体（禁止 JSON 字段）
- Redis 使用 List 结构存储

## Goals / Non-Goals

### Goals
- 实现 HybridChatMemoryService 分层存储服务
- Redis List 用于短期上下文窗口（滑动窗口裁剪）
- MySQL 用于长期历史全量存储
- 读取：优先 Redis，无数据查 MySQL 兜底
- 写入：先落库 MySQL，再操作 Redis

### Non-Goals
- 不使用 JSON 字段存储消息
- 不修改已有数据库表结构
- 不实现消息修改和删除功能

## Decisions

### Decision 1: ChatMemoryRepository 接口实现

Spring AI 的 `ChatMemoryRepository` 接口定义了以下方法：
```java
public interface ChatMemoryRepository {
    List<Message> findByConversationId(String conversationId);
    void saveAll(String conversationId, List<Message> messages);
    void deleteByConversationId(String conversationId);
}
```

我们将实现两个 Repository：
- `RedisChatMemoryRepository`：Redis List 实现
- `MySQLChatMemoryRepository`：MySQL + Mybatis-Plus 实现

### Decision 2: HybridChatMemoryService 整合逻辑

```java
@Service
@RequiredArgsConstructor
public class HybridChatMemoryService {

    private final RedisChatMemoryRepository redisRepo;
    private final MySQLChatMemoryRepository mysqlRepo;
    private final int maxMessages;

    /**
     * 读取：优先 Redis，无数据查 MySQL
     */
    public List<Message> getMessages(String conversationId) {
        List<Message> messages = redisRepo.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            messages = mysqlRepo.findRecentMessages(conversationId, maxMessages);
        }
        return messages;
    }

    /**
     * 写入：先落库 MySQL，再操作 Redis
     */
    public void addMessage(String conversationId, Message message) {
        mysqlRepo.saveMessage(conversationId, message);
        redisRepo.addMessage(conversationId, message, maxMessages);
    }

    /**
     * 清空：只删 Redis，保留 MySQL
     */
    public void clear(String conversationId) {
        redisRepo.deleteByConversationId(conversationId);
    }
}
```

### Decision 3: Redis List 轻量化存储

不在 Redis 中存储完整的 Message JSON（包含 @class 等元数据），而是存储轻量化结构：

```java
public class SimpleMessage {
    private String role;
    private String content;
}
```

Redis List 元素示例：
```
{"role": "user", "content": "你好"}
{"role": "assistant", "content": "您好！"}
```

### Decision 4: 数据转换

**Redis → Spring AI Message**：
```java
private Message toMessage(SimpleMessage simple) {
    return switch (simple.getRole()) {
        case "user" -> new UserMessage(simple.getContent());
        case "assistant" -> new AssistantMessage(simple.getContent());
        case "system" -> new SystemMessage(simple.getContent());
        default -> throw new IllegalArgumentException("Unknown role: " + simple.getRole());
    };
}
```

**MySQL Entity → Spring AI Message**：
```java
private Message toMessage(MessageHistoryEntity entity) {
    return switch (entity.getType()) {
        case 1 -> new UserMessage(entity.getContent());
        case 2 -> new AssistantMessage(entity.getContent());
        case 0 -> new SystemMessage(entity.getContent());
        default -> throw new IllegalArgumentException("Unknown type: " + entity.getType());
    };
}
```

### Decision 5: Redis 滑动窗口实现

```java
public void addMessage(String conversationId, Message message, int maxSize) {
    String key = buildKey(conversationId);
    String json = serialize(simpleMessage);

    // RPUSH: 右侧追加
    redisTemplate.opsForList().rightPush(key, json);

    // LTRIM: 裁剪窗口，只保留最新 maxSize 条
    redisTemplate.opsForList().trim(key, -maxSize, -1);

    // 续期 TTL
    redisTemplate.expire(key, ttl);
}
```

### Decision 6: MySQL 兜底查询

```java
public List<Message> findRecentMessages(String conversationId, int limit) {
    LambdaQueryWrapper<MessageHistoryEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(MessageHistoryEntity::getChatId, conversationId)
           .orderByAsc(MessageHistoryEntity::getCreateTime)
           .last("LIMIT " + limit);

    List<MessageHistoryEntity> entities = baseMapper.selectList(wrapper);
    return entities.stream().map(this::toMessage).collect(Collectors.toList());
}
```

## Components

### 1. RedisChatMemoryRepository

```java
@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX;
    private static final Duration TTL;

    @Value("${spring.ai.chat.memory.key-prefix:chat:memory:}")
    private void setKeyPrefix(String prefix) { KEY_PREFIX = prefix; }

    @Value("${spring.ai.chat.memory.redis-ttl-days:7}")
    private void setTtl(long days) { TTL = Duration.ofDays(days); }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }
        return jsonList.stream()
                .map(this::deserialize)
                .map(this::toMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        redisTemplate.delete(key);
        if (!messages.isEmpty()) {
            List<String> jsonList = messages.stream()
                    .map(this::toSimpleMessage)
                    .map(this::serialize)
                    .collect(Collectors.toList());
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, TTL);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    public void addMessage(String conversationId, Message message, int maxSize) {
        String key = buildKey(conversationId);
        String json = serialize(toSimpleMessage(message));
        redisTemplate.opsForList().rightPush(key, json);
        redisTemplate.opsForList().trim(key, -maxSize, -1);
        redisTemplate.expire(key, TTL);
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
```

### 2. MySQLChatMemoryRepository

```java
@Repository
@RequiredArgsConstructor
public class MySQLChatMemoryRepository {

    private final MessageHistoryMapper messageHistoryMapper;

    public void saveMessage(String conversationId, Message message) {
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getContent());
        entity.setType(detectMessageType(message));
        entity.setRole(detectRole(message));
        entity.setCreateTime(LocalDateTime.now());
        messageHistoryMapper.insert(entity);
    }

    public List<Message> findRecentMessages(String conversationId, int limit) {
        LambdaQueryWrapper<MessageHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageHistoryEntity::getChatId, conversationId)
               .orderByAsc(MessageHistoryEntity::getCreateTime)
               .last("LIMIT " + limit);
        return messageHistoryMapper.selectList(wrapper)
                .stream()
                .map(this::toMessage)
                .collect(Collectors.toList());
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
}
```

### 3. HybridChatMemoryService

```java
@Service
@RequiredArgsConstructor
public class HybridChatMemoryService implements ChatMemoryRepository {

    private final RedisChatMemoryRepository redisRepo;
    private final MySQLChatMemoryRepository mysqlRepo;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = redisRepo.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            messages = mysqlRepo.findRecentMessages(conversationId, maxMessages);
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            mysqlRepo.saveMessage(conversationId, message);
        }
        redisRepo.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisRepo.deleteByConversationId(conversationId);
    }
}
```

### 4. ChatMemoryConfig

```java
@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    private final HybridChatMemoryService hybridChatMemoryService;

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(
            @Value("${spring.ai.chat.memory.max-messages:10}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(hybridChatMemoryService)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(
            MessageWindowChatMemory messageWindowChatMemory) {
        return new MessageChatMemoryAdvisor(messageWindowChatMemory);
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

## Open Questions

1. **TTL 设置策略**：是否需要根据会话活跃度动态调整 TTL？
2. **并发写入**：MySQL 和 Redis 写入顺序如何保证一致性？
3. **异常处理**：Redis 写入失败时是否需要回滚 MySQL？

## Migration Plan

1. 创建新组件（不影响现有代码）
2. 配置新组件为 Spring Bean
3. 逐步切换使用新组件
4. 旧组件保留作为兼容

## SimpleMessage DTO

Redis List 存储的轻量化消息结构：

```java
package com.bigbear.ai.biz.dto;

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

## Complete Component Implementations

### 1. RedisChatMemoryRepository 完整实现

```java
package com.bigbear.ai.biz.infra.manager;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    @Value("${spring.ai.chat.memory.key-prefix:chat:memory:}")
    private String keyPrefix;

    @Value("${spring.ai.chat.memory.redis-ttl-days:7}")
    private long ttlDays;

    private Duration getTtl() {
        return Duration.ofDays(ttlDays);
    }

    private String buildKey(String conversationId) {
        return keyPrefix + conversationId;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return new ArrayList<>();
            }
            return jsonList.stream()
                    .map(this::deserialize)
                    .filter(Objects::nonNull)
                    .map(this::toMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis findByConversationId failed, key: {}", key, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
            List<String> jsonList = messages.stream()
                    .map(this::toSimpleMessage)
                    .map(this::serialize)
                    .collect(Collectors.toList());
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, getTtl());
        } catch (Exception e) {
            log.error("Redis saveAll failed, key: {}, messages: {}", key, messages.size(), e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis deleteByConversationId failed, key: {}", key, e);
        }
    }

    public void addMessage(String conversationId, Message message, int maxSize) {
        if (message == null) {
            return;
        }
        String key = buildKey(conversationId);
        try {
            String json = serialize(toSimpleMessage(message));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, getTtl());
        } catch (Exception e) {
            log.error("Redis addMessage failed, key: {}", key, e);
        }
    }

    private SimpleMessage toSimpleMessage(Message message) {
        String role = message.getMessageType().name().toLowerCase();
        return new SimpleMessage(role, message.getContent());
    }

    private Message toMessage(SimpleMessage simple) {
        if (simple == null || simple.getRole() == null) {
            return null;
        }
        return switch (simple.getRole().toLowerCase()) {
            case "user" -> new UserMessage(simple.getContent());
            case "assistant" -> new AssistantMessage(simple.getContent());
            case "system" -> new SystemMessage(simple.getContent());
            default -> {
                log.warn("Unknown role: {}, skipping message", simple.getRole());
                yield null;
            }
        };
    }

    private String serialize(SimpleMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Serialize SimpleMessage failed", e);
            return null;
        }
    }

    private SimpleMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json, SimpleMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize SimpleMessage failed, json: {}", json, e);
            return null;
        }
    }
}
```

### 2. MySQLChatMemoryRepository 完整实现

```java
package com.bigbear.ai.biz.infra.repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class MySQLChatMemoryRepository {

    private final MessageHistoryMapper messageHistoryMapper;

    public void saveMessage(String conversationId, Message message) {
        if (message == null) {
            return;
        }
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getContent());
        entity.setType(detectMessageType(message));
        entity.setRole(detectRole(message));
        entity.setCreateTime(LocalDateTime.now());
        try {
            messageHistoryMapper.insert(entity);
        } catch (Exception e) {
            log.error("MySQL saveMessage failed, conversationId: {}", conversationId, e);
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
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MySQL findRecentMessages failed, conversationId: {}", conversationId, e);
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
        if (entity == null) {
            return null;
        }
        return switch (entity.getType()) {
            case 1 -> new UserMessage(entity.getContent());
            case 2 -> new AssistantMessage(entity.getContent());
            case 0 -> new SystemMessage(entity.getContent());
            default -> {
                log.warn("Unknown message type: {}, skipping", entity.getType());
                yield null;
            }
        };
    }
}
```

### 3. HybridChatMemoryService 完整实现

```java
package com.bigbear.ai.biz.service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridChatMemoryService implements ChatMemoryRepository {

    private final RedisChatMemoryRepository redisRepo;
    private final MySQLChatMemoryRepository mysqlRepo;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = redisRepo.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            log.debug("Redis empty, fallback to MySQL, conversationId: {}", conversationId);
            messages = mysqlRepo.findRecentMessages(conversationId, maxMessages);
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        for (Message message : messages) {
            mysqlRepo.saveMessage(conversationId, message);
        }
        redisRepo.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        redisRepo.deleteByConversationId(conversationId);
    }

    public void addMessage(String conversationId, Message message) {
        if (conversationId == null || message == null) {
            return;
        }
        mysqlRepo.saveMessage(conversationId, message);
        redisRepo.addMessage(conversationId, message, maxMessages);
    }
}
```

### 4. ChatMemoryConfig 完整实现

```java
package com.bigbear.ai.biz.common.config;

@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    private final HybridChatMemoryService hybridChatMemoryService;

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(
            @Value("${spring.ai.chat.memory.max-messages:10}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(hybridChatMemoryService)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(
            MessageWindowChatMemory messageWindowChatMemory) {
        return new MessageChatMemoryAdvisor(messageWindowChatMemory);
    }
}
```

## Data Consistency Strategy

### Write Order Guarantee

写入顺序严格保证：MySQL 先 → Redis 后

```
┌──────────────────────────────────────────────────────────────┐
│                    saveAll(conversationId, messages)          │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  1. MySQL INSERT (每条消息)                                    │
│     - 失败 → 抛出异常，整个操作回滚                            │
│     - 成功 → 继续                                             │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  2. Redis saveAll (DEL + RPUSHALL + EXPIRE)                  │
│     - 失败 → 记录日志，MySQL 已写入成功                        │
│     - 下次读取从 MySQL 兜底                                   │
└──────────────────────────────────────────────────────────────┘
```

### Failure Handling

| 步骤 | 失败场景 | 处理方式 |
|------|---------|---------|
| MySQL INSERT | 连接超时、主键冲突 | 抛出异常，不执行 Redis |
| Redis DEL | 连接超时 | 重试 3 次，仍失败记录日志 |
| Redis RPUSH | 连接超时 | 重试 3 次，仍失败记录日志 |
| Redis EXPIRE | 连接超时 | 忽略，Key 最终会自动过期 |

## Redis vs MySQL Responsibilities

| 职责 | Redis List | MySQL |
|------|-----------|-------|
| 滑动窗口 | ✅ LTRIM 实现 | ❌ 不处理 |
| 短期上下文 | ✅ | ❌ |
| 全量历史 | ❌ 不存储 | ✅ INSERT 全量 |
| 兜底查询 | ❌ | ✅ |
| 会话清空 | ✅ DEL key | ❌ 保留 |
