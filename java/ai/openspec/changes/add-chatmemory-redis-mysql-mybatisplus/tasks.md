# Tasks: ChatMemory 分层存储实现（Redis List + MySQL + Mybatis-Plus）

## 1. DTO Layer
- [ ] 1.1 创建 `SimpleMessage` 类
  - [ ] 定义 `role` 和 `content` 字段
  - [ ] 实现 `Serializable` 接口
  - [ ] 提供无参构造函数
  - [ ] 提供全参构造函数
  - [ ] 实现 getter/setter 方法

## 2. Infrastructure Layer (Redis List)
- [ ] 2.1 创建 `RedisChatMemoryRepository`
  - [ ] 实现 `ChatMemoryRepository` 接口
  - [ ] 注入 `StringRedisTemplate` 和 `ObjectMapper`
  - [ ] 配置 Key 前缀（默认 `chat:memory:`）
  - [ ] 配置 TTL（默认 7 天，可动态配置）
  - [ ] 实现 `findByConversationId()` - 使用 `LRANGE key 0 -1`
    - [ ] 处理 Redis 连接异常
    - [ ] 处理反序列化异常
    - [ ] 未知角色跳过并记录日志
  - [ ] 实现 `saveAll()` - 使用 `DEL + RPUSHALL + EXPIRE`
    - [ ] 空消息列表直接返回
    - [ ] 异常捕获记录日志不抛错
  - [ ] 实现 `deleteByConversationId()` - 使用 `DEL key`
  - [ ] 实现 `addMessage()` - 使用 `RPUSH + LTRIM + EXPIRE`
  - [ ] 实现 `toSimpleMessage()` - Message → SimpleMessage
  - [ ] 实现 `toMessage()` - SimpleMessage → Message
  - [ ] 实现 `serialize()` - SimpleMessage → JSON String
  - [ ] 实现 `deserialize()` - JSON String → SimpleMessage
  - [ ] 添加日志记录

## 3. Infrastructure Layer (MySQL + Mybatis-Plus)
- [ ] 3.1 确认/创建 `MessageHistoryMapper`
  - [ ] 继承 `BaseMapper<MessageHistoryEntity>`
  - [ ] 确认对应 XML 或使用 Mybatis-Plus 注解
- [ ] 3.2 创建 `MySQLChatMemoryRepository`
  - [ ] 注入 `MessageHistoryMapper`
  - [ ] 实现 `saveMessage(String conversationId, Message message)`
    - [ ] 创建 `MessageHistoryEntity`
    - [ ] 设置 chatId, content, type, role, createTime
    - [ ] 调用 `messageHistoryMapper.insert()`
  - [ ] 实现 `findRecentMessages(String conversationId, int limit)`
    - [ ] 使用 `LambdaQueryWrapper`
    - [ ] 按 `chatId` 查询
    - [ ] 按 `createTime` 升序
    - [ ] LIMIT 限制数量
  - [ ] 实现 `detectMessageType()` - MessageType → int
  - [ ] 实现 `detectRole()` - MessageType → String
  - [ ] 实现 `toMessage()` - MessageHistoryEntity → Message
  - [ ] 添加日志记录
  - [ ] 异常处理

## 4. Service Layer (Hybrid)
- [ ] 4.1 创建 `HybridChatMemoryService`
  - [ ] 实现 `ChatMemoryRepository` 接口
  - [ ] 注入 `RedisChatMemoryRepository`
  - [ ] 注入 `MySQLChatMemoryRepository`
  - [ ] 配置 `maxMessages`（默认 10，可动态配置）
  - [ ] 实现 `findByConversationId()`
    - [ ] 优先查 Redis
    - [ ] Redis 空则查 MySQL 兜底
    - [ ] 空conversationId 返回空列表
  - [ ] 实现 `saveAll()`
    - [ ] 先落库 MySQL（逐条INSERT）
    - [ ] 再操作 Redis
    - [ ] 空参数校验
  - [ ] 实现 `deleteByConversationId()`
    - [ ] 只删 Redis
    - [ ] 空conversationId 校验
  - [ ] 实现 `addMessage()` 便捷方法
  - [ ] 添加日志记录

## 5. Configuration Layer
- [ ] 5.1 创建 `ChatMemoryConfig`
  - [ ] 创建 `MessageWindowChatMemory` Bean
    - [ ] 注入 `HybridChatMemoryService`
    - [ ] 设置 `maxMessages`
  - [ ] 创建 `MessageChatMemoryAdvisor` Bean
    - [ ] 注入 `MessageWindowChatMemory`
- [ ] 5.2 添加配置项到 `application.yml`
  - [ ] `spring.ai.chat.memory.max-messages: 10`
  - [ ] `spring.ai.chat.memory.redis-ttl-days: 7`
  - [ ] `spring.ai.chat.memory.key-prefix: "chat:memory:"`
- [ ] 5.3 确认 ObjectMapper Bean 配置
  - [ ] 确认已配置支持 Java 8 日期时间
  - [ ] 确认序列化配置正确

## 6. Integration
- [ ] 6.1 验证分层存储流程正确性
  - [ ] 验证读取流程：Redis → MySQL 兜底
  - [ ] 验证写入流程：MySQL → Redis
  - [ ] 验证清空流程：只删 Redis

## 7. Testing
- [ ] 7.1 单元测试
  - [ ] 测试 `RedisChatMemoryRepository`
    - [ ] 测试 `findByConversationId()` - 正常数据
    - [ ] 测试 `findByConversationId()` - 空数据
    - [ ] 测试 `saveAll()` - 正常保存
    - [ ] 测试 `deleteByConversationId()` - 正常删除
    - [ ] 测试 `addMessage()` - RPUSH + LTRIM
    - [ ] 测试滑动窗口裁剪
    - [ ] 测试 Redis 异常降级
  - [ ] 测试 `MySQLChatMemoryRepository`
    - [ ] 测试 `saveMessage()` - INSERT
    - [ ] 测试 `findRecentMessages()` - 正常查询
    - [ ] 测试消息类型转换
    - [ ] 测试空结果处理
  - [ ] 测试 `HybridChatMemoryService`
    - [ ] 测试 `findByConversationId()` - Redis 有数据
    - [ ] 测试 `findByConversationId()` - Redis 空，MySQL 兜底
    - [ ] 测试 `saveAll()` - 写入顺序
    - [ ] 测试 `deleteByConversationId()` - 只删 Redis
  - [ ] 测试消息类型转换
    - [ ] UserMessage ↔ type=1, role="user"
    - [ ] AssistantMessage ↔ type=2, role="assistant"
    - [ ] SystemMessage ↔ type=0, role="system"
- [ ] 7.2 集成测试
  - [ ] 测试 Redis List 滑动窗口（验证 LTRIM 效果）
  - [ ] 测试 MySQL 兜底查询
  - [ ] 测试清空会话只删 Redis
  - [ ] 测试写入顺序：MySQL 先，Redis 后
  - [ ] 测试 TTL 续期
- [ ] 7.3 端到端测试
  - [ ] 测试完整对话流程
  - [ ] 验证 Redis List 和 MySQL 数据一致性

## File Checklist

### New Files
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/dto/SimpleMessage.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/manager/RedisChatMemoryRepository.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/repository/MySQLChatMemoryRepository.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/service/HybridChatMemoryService.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/ChatMemoryConfig.java`

### Modified Files
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/SpringAiMCPConfig.java`
- [ ] `ai-biz/src/main/resources/application.yml`

### Reused Files (No Changes)
- `ai-biz/src/main/java/com/bigbear/ai/biz/domain/entity/MessageHistoryEntity.java`
- `ai-biz/src/main/java/com/bigbear/ai/biz/infra/mapper/MessageHistoryMapper.java`

## Implementation Summary

### 组件职责

| 组件 | 文件位置 | 职责 |
|------|---------|------|
| **RedisChatMemoryRepository** | `infra/manager/` | Redis List 存储，RPUSH/LTRIM/LRANGE |
| **MySQLChatMemoryRepository** | `infra/repository/` | MySQL 长期存储，INSERT + SELECT |
| **HybridChatMemoryService** | `service/` | 分层读写逻辑整合 |
| **ChatMemoryConfig** | `common/config/` | Spring AI 配置 |

### Redis 命令映射

| 功能 | Java 方法 | Redis 命令 |
|------|----------|-----------|
| 读取全部 | `findByConversationId()` | `LRANGE key 0 -1` |
| 保存全部 | `saveAll()` | `DEL + RPUSHALL + EXPIRE` |
| 裁剪窗口 | `addMessage()` | `RPUSH + LTRIM + EXPIRE` |
| 清空会话 | `deleteByConversationId()` | `DEL key` |
| 部分读取 | `getRecentMessages()` | `LRANGE key -n -1` |

### 分层读写流程

**读取流程**：
```
1. 调用 findByConversationId(conversationId)
2. 先查 Redis: LRANGE chat:memory:{id} 0 -1
3. 若 Redis 有数据 → 返回
4. 若 Redis 无数据 → 查 MySQL: SELECT * FROM memory WHERE chat_id = ? ORDER BY create_time LIMIT N
5. 返回消息列表
```

**写入流程**：
```
1. 调用 saveAll(conversationId, messages)
2. 先落库 MySQL: INSERT INTO memory (chat_id, content, type, role, create_time) VALUES (?, ?, ?, ?, ?)
3. 再操作 Redis: RPUSH chat:memory:{id} {json} + LTRIM chat:memory:{id} -N -1 + EXPIRE
```

**清空流程**：
```
1. 调用 deleteByConversationId(conversationId)
2. 只删 Redis: DEL chat:memory:{id}
3. MySQL 保留不删
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
