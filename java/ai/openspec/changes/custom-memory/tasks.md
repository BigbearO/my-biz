# Tasks: 自定义 ChatMemory 分层存储实现（Redis List + MySQL + Mybatis-Plus）

## 1. DTO Layer
- [ ] 1.1 创建 `SimpleMessage` 类
  - [ ] 定义 `role` 和 `content` 字段
  - [ ] 实现 `Serializable` 接口
  - [ ] 提供无参构造函数
  - [ ] 提供全参构造函数
  - [ ] 实现 getter/setter 方法

## 2. Infrastructure Layer (Redis List)
- [ ] 2.1 创建 `RedisChatMemoryStore`
  - [ ] 注入 `StringRedisTemplate` 和 `ObjectMapper`
  - [ ] 配置 Key 前缀（默认 `chat:memory:`）
  - [ ] 配置 TTL（默认 7 天，可动态配置）
  - [ ] 实现 `getMessages(String conversationId)` - 使用 `LRANGE key 0 -1`
    - [ ] 处理 Redis 连接异常，返回空列表
    - [ ] 处理反序列化异常，跳过并记录日志
    - [ ] 未知角色跳过并记录日志
  - [ ] 实现 `saveMessages(String conversationId, List<Message>)` - 使用 `DEL + RPUSHALL + EXPIRE`
    - [ ] 空消息列表直接返回
    - [ ] 异常捕获记录日志不抛错
  - [ ] 实现 `addMessage(String conversationId, Message, int maxSize)` - 使用 `RPUSH + LTRIM + EXPIRE`
    - [ ] 空消息直接返回
    - [ ] 一条消息对应一个 List 元素
    - [ ] 异常捕获记录日志不抛错
  - [ ] 实现 `deleteMessages(String conversationId)` - 使用 `DEL key`
  - [ ] 实现 `toSimpleMessage()` - Message → SimpleMessage
  - [ ] 实现 `toMessage()` - SimpleMessage → Message
  - [ ] 实现 `serialize()` - SimpleMessage → JSON String
  - [ ] 实现 `deserialize()` - JSON String → SimpleMessage
  - [ ] 添加日志记录

## 3. Infrastructure Layer (MySQL + Mybatis-Plus)
- [ ] 3.1 确认/创建 `MessageHistoryMapper`
  - [ ] 继承 `BaseMapper<MessageHistoryEntity>`
  - [ ] 确认对应 XML 或使用 Mybatis-Plus 注解
- [ ] 3.2 创建 `MySQLChatMemoryStore`
  - [ ] 注入 `MessageHistoryMapper`
  - [ ] 实现 `saveMessage(String conversationId, Message message)`
    - [ ] 创建 `MessageHistoryEntity`
    - [ ] 设置 chatId, content, type, role, createTime, updateTime
    - [ ] 调用 `messageHistoryMapper.insert()`
    - [ ] 添加 `@Transactional` 注解（遵循项目规范）
    - [ ] 只 INSERT，不 UPDATE/DELETE
  - [ ] 实现 `findRecentMessages(String conversationId, int limit)`
    - [ ] 使用 `LambdaQueryWrapper`
    - [ ] 按 `chatId` 查询
    - [ ] 按 `createTime` 升序
    - [ ] LIMIT 限制数量
    - [ ] 异常捕获记录日志，返回空列表
  - [ ] 实现 `detectMessageType()` - MessageType → int
  - [ ] 实现 `detectRole()` - MessageType → String
  - [ ] 实现 `toMessage()` - MessageHistoryEntity → Message
  - [ ] 添加日志记录
  - [ ] 异常处理

## 4. Service Layer (CustomChatMemory)
- [ ] 4.1 创建 `CustomChatMemory`
  - [ ] 直接实现 `org.springframework.ai.chat.memory.ChatMemory` 接口
  - [ ] 注入 `RedisChatMemoryStore`
  - [ ] 注入 `MySQLChatMemoryStore`
  - [ ] 配置 `maxMessages`（默认 10，可动态配置）
  - [ ] 实现 `get(String conversationId)`
    - [ ] 优先查 Redis
    - [ ] Redis 空则查 MySQL 兜底
    - [ ] MySQL 有数据则回填 Redis
    - [ ] 空 conversationId 返回空列表
  - [ ] 实现 `add(String conversationId, List<Message> messages)`
    - [ ] 先落库 MySQL（逐条 INSERT）
    - [ ] 再操作 Redis（逐条 RPUSH + LTRIM）
    - [ ] 空参数校验
    - [ ] null 消息跳过
  - [ ] 实现 `clear(String conversationId)`
    - [ ] 只删 Redis
    - [ ] 空 conversationId 校验
  - [ ] 添加日志记录

## 5. Configuration Layer
- [ ] 5.1 修改 `ChatMemoryConfig`
  - [ ] 注入 `CustomChatMemory`
  - [ ] 创建 `MessageChatMemoryAdvisor` Bean
    - [ ] 使用 `MessageChatMemoryAdvisor.builder(customChatMemory).build()`
  - [ ] 移除 `MessageWindowChatMemory` Bean（CustomChatMemory 自行管理窗口）
- [ ] 5.2 添加配置项到 `application-ai.yml`
  - [ ] `spring.ai.chat.memory.max-messages: 10`
  - [ ] `spring.ai.chat.memory.redis-ttl-days: 7`
  - [ ] `spring.ai.chat.memory.key-prefix: "chat:memory:"`
- [ ] 5.3 确认 ObjectMapper Bean 配置
  - [ ] 确认已配置支持 Java 8 日期时间
  - [ ] 确认序列化配置正确

## 6. Integration & Verification
- [ ] 6.1 验证分层存储流程正确性
  - [ ] 验证读取流程：Redis → MySQL 兜底 → 回填 Redis
  - [ ] 验证写入流程：MySQL → Redis
  - [ ] 验证清空流程：只删 Redis
  - [ ] 验证滑动窗口：LTRIM 裁剪

## 7. Testing
- [ ] 7.1 单元测试
  - [ ] 测试 `RedisChatMemoryStore`
    - [ ] 测试 `getMessages()` - 正常数据
    - [ ] 测试 `getMessages()` - 空数据
    - [ ] 测试 `saveMessages()` - 正常保存
    - [ ] 测试 `deleteMessages()` - 正常删除
    - [ ] 测试 `addMessage()` - RPUSH + LTRIM
    - [ ] 测试滑动窗口裁剪（超出 maxMessages 自动裁剪）
    - [ ] 测试 Redis 异常降级
  - [ ] 测试 `MySQLChatMemoryStore`
    - [ ] 测试 `saveMessage()` - INSERT
    - [ ] 测试 `findRecentMessages()` - 正常查询
    - [ ] 测试消息类型转换（User/Assistant/System）
    - [ ] 测试空结果处理
  - [ ] 测试 `CustomChatMemory`
    - [ ] 测试 `get()` - Redis 有数据
    - [ ] 测试 `get()` - Redis 空，MySQL 兜底并回填
    - [ ] 测试 `add()` - 写入顺序（MySQL 先，Redis 后）
    - [ ] 测试 `clear()` - 只删 Redis
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
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/manager/RedisChatMemoryStore.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/repository/MySQLChatMemoryStore.java`
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/manager/CustomChatMemory.java`

### Modified Files
- [ ] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/ChatMemoryConfig.java`
- [ ] `ai-biz/src/main/resources/application-ai.yml`

### Reused Files (No Changes)
- `ai-biz/src/main/java/com/bigbear/ai/biz/domain/entity/MessageHistoryEntity.java`
- `ai-biz/src/main/java/com/bigbear/ai/biz/infra/mapper/MessageHistoryMapper.java`

## Implementation Summary

### 组件职责

| 组件 | 文件位置 | 职责 |
|------|---------|------|
| **SimpleMessage** | `dto/` | Redis List 轻量化存储 DTO（role + content） |
| **RedisChatMemoryStore** | `infra/manager/` | Redis List 存储操作封装，RPUSH / LTRIM / LRANGE / DEL |
| **MySQLChatMemoryStore** | `infra/repository/` | MySQL 长期存储操作封装，INSERT + SELECT |
| **CustomChatMemory** | `infra/manager/` | 直接实现 `ChatMemory` 接口，分层读写整合，自行管理窗口 |
| **ChatMemoryConfig** | `common/config/` | Spring AI 配置，注册 `MessageChatMemoryAdvisor` |

### Redis 命令映射

| 功能 | Java 方法 | Redis 命令 |
|------|----------|-----------|
| 读取全部 | `getMessages()` | `LRANGE key 0 -1` |
| 保存全部 | `saveMessages()` | `DEL + RPUSHALL + EXPIRE` |
| 追加单条 | `addMessage()` | `RPUSH + LTRIM + EXPIRE` |
| 清空会话 | `deleteMessages()` | `DEL key` |

### 分层读写流程

**读取流程**：
```
1. 调用 CustomChatMemory.get(conversationId)
2. 先查 Redis: LRANGE chat:memory:{id} 0 -1
3. 若 Redis 有数据 → 返回
4. 若 Redis 无数据 → 查 MySQL: SELECT * FROM message_history WHERE chat_id = ? ORDER BY create_time LIMIT N
5. MySQL 有数据 → 回填 Redis: DEL + RPUSHALL + EXPIRE
6. 返回消息列表
```

**写入流程**：
```
1. 调用 CustomChatMemory.add(conversationId, messages)
2. 先落库 MySQL: INSERT INTO message_history (chat_id, content, type, role, create_time) VALUES (?, ?, ?, ?, ?)
3. 再操作 Redis: RPUSH chat:memory:{id} {json} + LTRIM chat:memory:{id} -N -1 + EXPIRE
```

**清空流程**：
```
1. 调用 CustomChatMemory.clear(conversationId)
2. 只删 Redis: DEL chat:memory:{id}
3. MySQL 保留不删
```
