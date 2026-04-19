# Tasks: ChatMemory 分层存储架构实现（Redis List 版）

## 1. Infrastructure Layer (Redis List)
- [x] 1.1 创建 `RedisChatMemoryRepository`
  - [x] 实现 `ChatMemoryRepository` 接口
  - [x] 实现 `findConversationIds()` 方法
  - [x] 实现 `findByConversationId()` 方法 - 使用 `LRANGE key 0 -1`
  - [x] 实现 `saveAll()` 方法 - 使用 `DEL + RPUSHALL` 模式
  - [x] 实现 `deleteByConversationId()` 方法
  - [x] 实现 `trimToSize()` 方法 - 使用 `LTRIM key -N -1`
  - [x] 可选：实现 `addMessage()` 增量追加方法
  - [x] 配置 Key 前缀 `chat:memory:` 和 TTL（7 天）
- [x] 1.2 配置 Redis List 序列化
  - [x] 每条消息单独 Jackson 序列化
  - [x] 配置多态类型支持（@class 字段）
  - [x] 处理序列化/反序列化异常

## 2. Service Layer (Long-term Storage)
- [x] 2.1 创建 `ChatHistoryService`
  - [x] 实现 `saveMessage(String conversationId, Message message)` 方法
    - [x] 保存单条消息到 memory 表（INSERT 操作）
    - [x] 自动识别消息类型（User/Assistant/System）
    - [x] 设置 role 字段
  - [x] 实现 `getFullHistory(String conversationId)` 方法
    - [x] 查询 memory 表获取全量历史
    - [x] 按时间戳升序排序
  - [x] 可选：实现 `getHistoryByUserId(String userId)` 方法
  - [x] 可选：实现 `getHistoryByItemId(String itemId)` 方法
- [x] 2.2 复用现有组件
  - [x] 注入 `MessageHistoryManager` 进行数据库操作
  - [x] 复用 `MessageHistoryEntity` 实体类

## 3. Configuration Layer
- [x] 3.1 创建 `JacksonConfig`
  - [x] 配置 `ObjectMapper` Bean
  - [x] 启用默认类型信息（`activateDefaultTyping`）
  - [x] 设置 `LaissezFaireSubTypeValidator`
  - [x] 配置 `JsonTypeInfo.As.PROPERTY`
- [x] 3.2 创建 `ChatMemoryConfig`
  - [x] 配置 `MessageWindowChatMemory` Bean
    - [x] 注入 `RedisChatMemoryRepository`
    - [x] 设置 `maxMessages`（默认 10）
  - [x] 配置 `MessageChatMemoryAdvisor` Bean
  - [x] 可选：配置 `ChatClient` Bean（如需要）
- [x] 3.3 更新 `SpringAiMCPConfig`
  - [x] 注入 `MessageWindowChatMemory` 替代 `MyChatMemory`
  - [x] 保留旧版配置用于兼容
- [x] 3.4 添加配置项到 `application.yml`
  - [x] `spring.ai.chat.memory.max-messages: 10`

## 4. Integration & Usage
- [x] 4.1 修改/创建业务服务
  - [x] 创建 `ChatClientService` 作为整合示例
  - [x] 注入 `ChatClient` 和 `ChatHistoryService`
  - [x] 实现对话流程：
    1. 保存用户消息到长期存储（PostgreSQL）
    2. 调用 ChatClient（自动从 Redis List 读取窗口上下文）
    3. 保存 AI 回复到长期存储（PostgreSQL）
- [x] 4.2 创建使用示例
  - [x] 编写 `ChatClientService` 示例代码
  - [x] 展示如何获取全量历史记录

## 5. Advanced Optimizations (Optional)
- [ ] 5.1 Pipeline 优化
  - [ ] 使用 `executePipelined` 批量执行 DEL + RPUSH + EXPIRE
- [x] 5.2 增量更新优化
  - [x] 实现 `addMessage()` 方法（RPUSH + LTRIM + EXPIRE）
- [x] 5.3 部分读取优化
  - [x] 实现 `getRecentMessages(conversationId, n)` 方法
  - [x] 使用 `LRANGE key -n -1` 读取最近 N 条

## 6. Testing
- [ ] 6.1 单元测试
  - [ ] 测试 `RedisChatMemoryRepository` 独立功能
    - [ ] 测试 `RPUSH/LRANGE/LTRIM` 操作
    - [ ] 测试滑动窗口裁剪逻辑
  - [ ] 测试 `ChatHistoryService` 独立功能
  - [ ] 测试消息类型识别逻辑
- [ ] 6.2 集成测试
  - [ ] 测试 Redis List 滑动窗口（验证 LTRIM 效果）
  - [ ] 测试全量历史存储（验证来一条存一条）
  - [ ] 测试多态序列化/反序列化
- [ ] 6.3 端到端测试
  - [ ] 测试完整对话流程
  - [ ] 验证 Redis List 和 PostgreSQL 各自正确性

## File Checklist

### New Files
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/JacksonConfig.java`
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/ChatMemoryConfig.java`
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/infra/manager/RedisChatMemoryRepository.java`
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/service/ChatHistoryService.java`
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/service/ChatClientService.java`

### Modified Files
- [x] `ai-biz/src/main/java/com/bigbear/ai/biz/common/config/SpringAiMCPConfig.java`
- [x] `ai-biz/src/main/resources/application.yml`

### Reused Files (No Changes)
- `ai-biz/src/main/java/com/bigbear/ai/biz/domain/entity/MessageHistoryEntity.java`
- `ai-biz/src/main/java/com/bigbear/ai/biz/infra/manager/MessageHistoryManager.java`
- `ai-biz/src/main/java/com/bigbear/ai/biz/infra/mapper/MessageHistoryMapper.java`

## Implementation Summary

### 核心实现

| 组件 | 文件 | 功能 |
|------|------|------|
| **JacksonConfig** | `common/config/JacksonConfig.java` | 配置 ObjectMapper 多态序列化 |
| **RedisChatMemoryRepository** | `infra/manager/RedisChatMemoryRepository.java` | Redis List 存储，LRANGE/LTRIM/RPUSH |
| **ChatMemoryConfig** | `common/config/ChatMemoryConfig.java` | MessageWindowChatMemory 配置 |
| **ChatHistoryService** | `service/ChatHistoryService.java` | PostgreSQL 长期存储 |
| **ChatClientService** | `service/ChatClientService.java` | 整合使用示例 |

### Redis 命令映射

| 功能 | Java 方法 | Redis 命令 |
|------|----------|-----------|
| 读取全部 | `findByConversationId()` | `LRANGE key 0 -1` |
| 保存全部 | `saveAll()` | `DEL + RPUSHALL + EXPIRE` |
| 裁剪窗口 | `trimToSize()` | `LTRIM key -N -1` |
| 增量追加 | `addMessage()` | `RPUSH + LTRIM + EXPIRE` |
| 部分读取 | `getRecentMessages()` | `LRANGE key -n -1` |
| 获取长度 | `getMessageCount()` | `LLEN key` |

### 存储层职责

| 存储层 | 职责 | 数据结构 | 写入方式 |
|--------|------|---------|---------|
| **Redis List** | 短期上下文（滑动窗口） | List | RPUSH + LTRIM |
| **PostgreSQL** | 长期历史（全量记录） | 行记录 | INSERT |

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

# 删除会话
DEL chat:memory:session_001
```
