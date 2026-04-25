# Change: 自定义 ChatMemory 分层存储实现（Redis List + MySQL + Mybatis-Plus）

## Why
当前系统需要为 Spring AI 提供一套自定义的 ChatMemory 实现，直接实现 `org.springframework.ai.chat.memory.ChatMemory` 接口，整合 Redis List 与 MySQL 实现分层存储：
1. **Redis List**：作为短期上下文窗口，提供高效的滑动窗口裁剪能力（RPUSH / LTRIM / LRANGE），支撑 LLM 快速读取对话上下文。
2. **MySQL**：作为长期历史存储，全量持久化消息记录，用于数据归档与兜底查询。
3. **Spring AI 1.1.4**：直接实现 `ChatMemory` 接口，仿照原生 `MessageWindowChatMemory` 窗口逻辑，自行管理消息窗口裁剪。

核心设计原则：
- 直接实现 `ChatMemory` 接口（`add` / `get` / `clear`），不依赖 `ChatMemoryRepository`。
- 读取优先查 Redis，无数据再查 MySQL 兜底，并回填 Redis。
- 写入先落库 MySQL，再操作 Redis。
- 窗口逻辑仅在 Redis 层处理，数据库不分页删除、不截断。
- MySQL 只新增、不修改、不删除；字段结构化存储，禁止 JSON。

## What Changes
- **MODIFIED** 更新 `CustomChatMemory.add()`：集成 `MessageWindowChatMemory.process()` 完整逻辑。
  - 新增 `process()` 方法：合并已有消息与新消息，处理 SystemMessage 替换策略。
  - SystemMessage 替换：当新消息包含不在已有记忆中的 SystemMessage 时，丢弃所有旧 SystemMessage。
  - 滑动窗口裁剪：超出 `maxMessages` 时，优先从头部移除非 SystemMessage 消息。
  - `add()` 流程更新：先读 Redis 已有消息 → 调用 `process()` 合并 → 写 MySQL（全量新消息）→ 写 Redis（处理后的消息列表）。
- **ADDED** 新增 `CustomChatMemory`：直接实现 `org.springframework.ai.chat.memory.ChatMemory` 接口。
  - 自行管理滑动窗口逻辑（仿照 `MessageWindowChatMemory`）。
  - `add()`：先写 MySQL，再 RPUSH 到 Redis + LTRIM 裁剪窗口。
  - `get()`：先读 Redis，无数据则读 MySQL 兜底并回填 Redis。
  - `clear()`：只删 Redis Key，MySQL 全量保留。
- **ADDED** 新增 `SimpleMessage` DTO：Redis List 轻量化存储结构（role + content）。
- **ADDED** 新增 `RedisChatMemoryStore`：基于 Redis List 的存储操作封装。
  - 使用 RPUSH 追加消息，LTRIM 实现滑动窗口裁剪。
  - 支持 LRANGE 按需读取消息。
  - 配置可动态设置的窗口大小 N、Key 前缀、TTL。
- **ADDED** 新增 `MySQLChatMemoryStore`：基于 Mybatis-Plus 的持久化存储封装。
  - 使用 `MessageHistoryEntity` 结构化存储（role + content + type，禁止 JSON 字段）。
  - 按 `conversationId` + 创建时间正序查询最新 N 条。
  - 全量追加存储，只 INSERT、不 UPDATE、不 DELETE。
- **ADDED** 新增 `ChatMemoryConfig`：Spring AI 1.1.4 配置类，将 `CustomChatMemory` 注册为 Bean。
- **ADDED** 新增 `application-ai.yml` / `application.yml` 配置项：`max-messages`、`redis-ttl-days`、`key-prefix`。

## Impact
- **Affected specs**: ai-biz（对话记忆管理）
- **Affected code**:
  - `ai-biz/infra/manager/` 新增 `CustomChatMemory`
  - `ai-biz/infra/manager/` 新增 `RedisChatMemoryStore`
  - `ai-biz/infra/repository/` 新增 `MySQLChatMemoryStore`
  - `ai-biz/common/config/` 新增 `ChatMemoryConfig`
  - `ai-biz/dto/` 新增 `SimpleMessage`
  - `ai-biz/domain/entity/` 复用 `MessageHistoryEntity`
  - `ai-biz/infra/mapper/` 复用 `MessageHistoryMapper`
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
│                         CustomChatMemory                                     │
│                    (直接实现 ChatMemory 接口)                                 │
│                    /          |          \                                  │
│                   ↓           ↓           ↓                                  │
│         窗口裁剪逻辑    RedisChatMemoryStore   MySQLChatMemoryStore          │
│         (maxMessages)   (Redis List)          (MySQL + Mybatis-Plus)        │
│                         Key: chat:memory:{id}  Table: message_history        │
│                         Type: List             MessageHistoryEntity          │
│                         Elements: role+content 字段: id, chat_id, content,  │
│                         (轻量化 JSON)           type, role, create_time      │
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
1. 调用 CustomChatMemory.get(conversationId)
2. 先查 Redis List（LRANGE key 0 -1）
3. 若 Redis 有数据 → 返回
4. 若 Redis 无数据 → 查 MySQL 兜底（按 conversationId + 创建时间 LIMIT N）
5. MySQL 有数据 → 回填 Redis（DEL + RPUSHALL + EXPIRE）
6. 返回消息列表
```

### 写入策略
```
1. 调用 CustomChatMemory.add(conversationId, messages)
2. 读取 Redis 已有消息（memoryMessages）
3. 调用 process(memoryMessages, newMessages) 合并消息：
   - 检测新消息中是否有新的 SystemMessage（不在已有记忆中）
   - 若有新 SystemMessage，丢弃所有旧 SystemMessage
   - 追加所有新消息
   - 若总消息数超过 maxMessages，从头部开始移除非 SystemMessage
4. 先落库 MySQL（逐条 INSERT，保存所有新消息）
5. 再操作 Redis（DEL + RPUSHALL 处理后的消息列表 + EXPIRE 续期）
6. 若 MySQL 失败，Redis 不执行
```

### 清空策略
```
1. 调用 CustomChatMemory.clear(conversationId)
2. 只删 Redis Key（DEL key）
3. MySQL 全量历史保留不删
```

### 滑动窗口逻辑
```
1. 配置文件中指定窗口大小 N（maxMessages）
2. Redis 层使用 LTRIM key -N -1 裁剪，只保留最近 N 条
3. 数据库不分页删除、不截断
```
