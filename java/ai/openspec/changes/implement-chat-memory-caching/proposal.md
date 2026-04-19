# Change: 实现 ChatMemory 分层存储架构（Redis List 优化）

## Why
当前系统使用 MyChatMemory 实现对话记忆功能,但存在以下问题:
1. **性能瓶颈**: 每次对话都需要查询数据库,高并发场景下数据库压力大
2. **存储方式不一致**: 同时存在 MyChatMemory(Spring AI)和 DbChatMemoryStore(LangChain4j)两种实现,维护成本高
3. **缺乏分层设计**: 没有区分"短期上下文"和"长期历史"的不同存储需求
4. **Redis 存储效率低**: 现有 String 类型需要覆盖整个 Key,滑动窗口需 Java 代码裁剪

通过引入**分层存储架构**并优化为 **Redis List**:
- **Redis List**: 存储短期上下文(滑动窗口),支持 RPUSH/LTRIM 原生操作
- **PostgreSQL**: 存储长期历史(来一条存一条),用于数据分析和审计

可以显著提升对话上下文的读取和更新性能。

## What Changes
- **ADDED** 新增 `RedisChatMemoryRepository`: 基于 Redis List 实现 ChatMemoryRepository,支持:
  - RPUSH 增量追加消息
  - LTRIM 原生滑动窗口裁剪
  - LRANGE 按需读取范围消息
- **ADDED** 新增 `ChatHistoryService`: 长期存储服务,将来一条消息保存到 PostgreSQL
- **ADDED** 新增 `JacksonConfig`: 配置 ObjectMapper 支持 Message 多态序列化
- **ADDED** 新增 `ChatMemoryConfig`: Spring AI 配置类,整合 MessageWindowChatMemory 和 Advisor
- **MODIFIED** 复用现有的 `memory` 表(MessageHistoryEntity): 作为长期历史存储
- **REMOVED** 废弃 `PostgreSqlChatMemoryRepository` 作为 Spring AI 存储后端的方案
- **REMOVED** 废弃 `CachingChatMemoryRepository` 双层缓存装饰器
- **REMOVED** 不使用 Redis String 类型(改为 List 类型)

## Impact
- **Affected specs**: ai-biz (对话记忆管理)
- **Affected code**: 
  - `infra/manager/` 目录下新增 RedisChatMemoryRepository
  - `service/` 目录下新增 ChatHistoryService
  - `common/config/` 目录下新增配置类
- **Dependencies**: 
  - Spring AI 1.1.4 (已存在)
  - Redis (已存在,使用 List 数据结构)
  - Jackson (已存在)
  - 复用现有的 MessageHistoryManager 和 memory 表
- **Breaking Changes**: 
  - 废弃现有的 `MyChatMemory` 实现(保留作为兼容)
  - 不需要修改 `chat_memory` 表结构

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

## Redis List vs String 对比

| 特性 | String（旧方案） | List（新方案） |
|------|-----------------|---------------|
| **滑动窗口实现** | Java 代码裁剪后 SET | **LTRIM 原生命令** |
| **增量更新** | 覆盖整个 Key | **RPUSH 追加单条** |
| **部分读取** | 读取全部后裁剪 | **LRANGE 按需读取** |
| **网络开销** | 传输整个 JSON | **只传输需要的数据** |
| **时间复杂度** | O(N) 序列化 | **O(1) RPUSH/LTRIM** |

## Storage Strategy

| 存储层 | 存储内容 | 数据结构 | 写入方式 | 用途 |
|--------|---------|---------|---------|------|
| **Redis** | 滑动窗口消息 | List | RPUSH + LTRIM | LLM 快速读取上下文 |
| **PostgreSQL** | 全量消息历史 | 行记录 | INSERT | 数据分析、审计 |

## Redis List Structure

```
Key: chat:memory:{conversationId}
Type: List
Elements:
  0: {"@class":"UserMessage","content":"你好"}
  1: {"@class":"AssistantMessage","content":"您好！"}
  2: {"@class":"UserMessage","content":"今天天气如何"}
  3: {"@class":"AssistantMessage","content":"今天晴天..."}
TTL: 7天

Commands:
  - RPUSH: 追加新消息
  - LTRIM -10 -1: 保留最近10条
  - LRANGE 0 -1: 读取全部
  - LLEN: 获取消息数量
```

## References
- [Spring AI 自定义数据库持久化的ChatMemory](https://juejin.cn/post/7513492533638053922)
- [Spring AI 聊天记忆功能实战：自定义 Redis 聊天记忆外部存储实现](https://www.cnblogs.com/cyanty/p/18935814)
- [Spring AI 会话记忆实战：从内存存储到 MySQL + Redis 双层缓存架构](https://www.cnblogs.com/yangykaifa/p/19198793)
- [Redis List 命令文档](https://redis.io/commands/?group=list)
