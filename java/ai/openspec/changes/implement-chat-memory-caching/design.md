# Design: ChatMemory 分层存储架构设计（Redis List 优化版）

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Spring AI 架构                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                       MessageChatMemoryAdvisor                               │
│                                    ↓                                         │
│                    MessageWindowChatMemory (滑动窗口)                         │
│                                    ↓                                         │
│                      RedisChatMemoryRepository                               │
│                      (Redis List - 滑动窗口优化)                              │
│                                    ↓                                         │
│                      Redis List (LRANGE/LTRIM 优化)                          │
│                          Key: chat:memory:{id}                               │
│                          Type: List                                          │
│                          Elements: 单条消息 JSON                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ 独立写入（来一条存一条）
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                         长期存储 (PostgreSQL)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                      ChatHistoryService                                      │
│                    (来一条存一条,全量历史记录)                                │
│                                    ↓                                         │
│                         memory 表 (现有)                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Redis 存储结构对比

### 旧方案（String）
```
Key: chat:memory:session_001
Type: String
Value: JSON 数组（整个消息列表序列化）
["{...}", "{...}", "{...}"]

问题：
- 每次更新需要覆盖整个 Key
- 滑动窗口需 Java 代码裁剪
```

### 新方案（List）⭐
```
Key: chat:memory:session_001
Type: List
Elements:
  0: {"@class":"UserMessage","content":"你好"}
  1: {"@class":"AssistantMessage","content":"您好！"}
  2: {"@class":"UserMessage","content":"今天天气如何"}
  3: {"@class":"AssistantMessage","content":"今天晴天..."}

优势：
- 增量追加（RPUSH）
- 原生命令裁剪（LTRIM）
- 按需读取（LRANGE）
```

## Components

### 1. RedisChatMemoryRepository（List 优化版）

```java
@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofDays(7);
    
    /**
     * 批量保存消息（覆盖式）
     * MessageWindowChatMemory 调用此方法
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        
        // 1. 删除旧数据
        redisTemplate.delete(key);
        
        // 2. 批量追加消息
        List<String> jsonList = messages.stream()
                .map(this::serializeMessage)
                .toList();
        
        if (!jsonList.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, TTL);
        }
    }
    
    /**
     * 读取全部消息
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        
        // LRANGE key 0 -1：读取全部元素
        List<String> jsonList = redisTemplate.opsForList()
                .range(key, 0, -1);
        
        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }
        
        return jsonList.stream()
                .map(this::deserializeMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 优化：使用 LTRIM 实现滑动窗口
     * 比 Java 代码裁剪更高效
     */
    public void trimToSize(String conversationId, int maxSize) {
        String key = buildKey(conversationId);
        // 只保留最后 maxSize 条（负数索引 -maxSize 到 -1）
        redisTemplate.opsForList().trim(key, -maxSize, -1);
    }
    
    /**
     * 增量追加单条消息（可选优化）
     */
    public void addMessage(String conversationId, Message message, int maxSize) {
        String key = buildKey(conversationId);
        String json = serializeMessage(message);
        
        // RPUSH：右侧追加
        redisTemplate.opsForList().rightPush(key, json);
        
        // LTRIM：裁剪窗口
        redisTemplate.opsForList().trim(key, -maxSize, -1);
        
        // 续期 TTL
        redisTemplate.expire(key, TTL);
    }
}
```

### 2. Redis List 命令映射

| 功能 | 原 String 方案 | 新 List 方案 |
|------|---------------|-------------|
| **保存全部** | SET key json | DEL + RPUSHALL |
| **追加消息** | SET key json（覆盖）| RPUSH key element |
| **读取全部** | GET key | LRANGE key 0 -1 |
| **读取部分** | GET key + Java 裁剪 | LRANGE key -N -1 |
| **滑动窗口** | Java 代码裁剪 | LTRIM key -N -1 |
| **删除会话** | DEL key | DEL key |
| **获取长度** | JSON 解析 | LLEN key |

### 3. ChatHistoryService（长期存储）

保持不变，使用 PostgreSQL `memory` 表：

```java
@Service
@RequiredArgsConstructor
public class ChatHistoryService {
    
    private final MessageHistoryManager messageHistoryManager;
    
    /**
     * 保存单条消息（来一条存一条）
     */
    @Transactional
    public void saveMessage(String conversationId, Message message) {
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getContent());
        entity.setType(detectMessageType(message));
        entity.setRole(detectRole(message));
        entity.setCreateTime(LocalDateTime.now());
        messageHistoryManager.save(entity);
    }
    
    private Integer detectMessageType(Message message) {
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

## Data Flow

### 对话流程（写入）
```
用户发送消息
    ↓
ChatHistoryService.saveMessage() → PostgreSQL（INSERT）
    ↓
调用 ChatClient
    ↓
MessageWindowChatMemory 管理滑动窗口
    ↓
RedisChatMemoryRepository.saveAll()
    ↓
┌─────────────────────────────────────┐
│  Redis List 操作（原子）               │
│  1. DEL chat:memory:{id}             │
│  2. RPUSHALL elements...             │
│  3. EXPIRE 7d                        │
└─────────────────────────────────────┘
```

### 读取流程
```
ChatClient 请求对话
    ↓
MessageChatMemoryAdvisor 获取上下文
    ↓
MessageWindowChatMemory 调用 findByConversationId()
    ↓
Redis LRANGE chat:memory:{id} 0 -1
    ↓
返回最近 10 条消息
```

## Configuration

### Spring AI 配置
```java
@Configuration
public class ChatMemoryConfig {
    
    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(
            RedisChatMemoryRepository redisRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisRepository)
                .maxMessages(10)  // 滑动窗口：只保留最近10条
                .build();
    }
    
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(
            MessageWindowChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
    
    @Bean
    public ChatClient chatClient(
            OpenAiChatModel chatModel,
            MessageChatMemoryAdvisor chatMemoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(chatMemoryAdvisor)
                .build();
    }
}
```

### Jackson 配置（多态序列化）
```java
@Configuration
public class JacksonConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 启用多态类型信息
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return mapper;
    }
}
```

## Usage Example

```java
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;
    
    public String chat(String conversationId, String userMessage) {
        // 1. 保存用户消息到长期存储
        chatHistoryService.saveMessage(
            conversationId, 
            new UserMessage(userMessage)
        );
        
        // 2. 调用 ChatClient（自动从 Redis List 读取窗口上下文）
        String response = chatClient.prompt()
                .user(userMessage)
                .advisors(spec -> spec
                    .param("chat_memory_conversation_id", conversationId)
                )
                .call()
                .content();
        
        // 3. 保存 AI 回复到长期存储
        chatHistoryService.saveMessage(
            conversationId,
            new AssistantMessage(response)
        );
        
        return response;
    }
}
```

## Performance Comparison

### String vs List

| 操作 | String 方案 | List 方案 | 优势 |
|------|------------|-----------|------|
| **读取 10 条** | GET + JSON 解析 | LRANGE 0 -1 | 持平 |
| **滑动窗口** | Java 裁剪 + SET | LTRIM | **List 优**（Redis 原生） |
| **增量追加** | GET + 修改 + SET | RPUSH | **List 优**（O(1)） |
| **读取最近 5 条** | GET 全部 + Java 裁剪 | LRANGE -5 -1 | **List 优**（网络少） |
| **获取消息数** | JSON 数组长度 | LLEN | **List 优**（O(1)） |
| **存储空间** | 紧凑 JSON | 稍大（List 元数据） | String 略优 |

## Advanced Optimizations

### 1. Pipeline 批量操作
```java
public void saveAllBatch(String conversationId, List<Message> messages) {
    String key = buildKey(conversationId);
    
    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        connection.del(key.getBytes());
        for (Message msg : messages) {
            connection.rPush(key.getBytes(), serializeMessage(msg).getBytes());
        }
        connection.expire(key.getBytes(), TTL.getSeconds());
        return null;
    });
}
```

### 2. 增量更新（避免覆盖）
```java
public void appendAndTrim(String conversationId, Message message, int maxSize) {
    String key = buildKey(conversationId);
    String json = serializeMessage(message);
    
    redisTemplate.execute((RedisCallback<Object>) connection -> {
        // 使用 List 命令链
        connection.rPush(key.getBytes(), json.getBytes());
        connection.lTrim(key.getBytes(), -maxSize, -1);
        connection.expire(key.getBytes(), TTL.getSeconds());
        return null;
    });
}
```

### 3. 部分读取（未来扩展）
```java
// 只读取最近 5 条，不读取全部
public List<Message> getRecentMessages(String conversationId, int n) {
    String key = buildKey(conversationId);
    
    // LRANGE key -N -1：读取最后 N 条
    List<String> jsonList = redisTemplate.opsForList()
            .range(key, -n, -1);
    
    return deserializeMessages(jsonList);
}
```

## Error Handling

### 降级策略
- **Redis 故障**：从 PostgreSQL 读取全量历史，重建 Redis List
- **序列化失败**：单条消息失败不影响其他消息

### 数据一致性
- **短期不一致**：Redis List 与 PostgreSQL 不强制实时一致
- **最终一致性**：通过业务逻辑保证（先存 PostgreSQL，再更新 Redis）

## Monitoring

### Redis 监控指标
```
# List 长度监控
LLEN chat:memory:{conversationId}

# 内存占用（每条消息平均大小）
DEBUG OBJECT chat:memory:{conversationId}

# Key 过期时间
TTL chat:memory:{conversationId}
```
