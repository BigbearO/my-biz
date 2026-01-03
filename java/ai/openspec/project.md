# Project Context

## Purpose
AI 业务系统,提供智能客服、对话管理、RAG(检索增强生成)、媒体内容识别等功能。主要用于闲鱼商品智能回复、客户服务自动化等场景。

## Tech Stack
- **Java**: 21
- **Spring Boot**: 基于 RuoYi-Cloud-Plus 2.5.2 框架
- **LangChain4j**: 1.9.1 (主要 AI 框架)
- **Spring AI**: 1.1.2
- **Spring AI Alibaba**: 1.0.0.4
- **LangGraph4j**: 1.7.8 (图工作流)
- **数据库**: PostgreSQL
- **ORM**: MyBatis-Plus + MyBatis-Plus-Join
- **配置中心**: Nacos
- **向量数据库**: PGVector (LangChain4j 集成)

### AI 能力
- 多模型支持: OpenAI、DashScope (阿里云)
- MCP (Model Context Protocol) 客户端: LangChain4j-MCP、Spring AI MCP
- 文档处理: Tika
- 向量嵌入: all-minilm-l6-v2

## Project Conventions

### Code Style
- 遵循 Java 21 语法规范
- 使用驼峰命名法 (camelCase) 变量和方法
- 使用帕斯卡命名法 (PascalCase) 类名
- 数据库操作添加 `@Transactional` 注解
- 包结构: `com.bigbear.ai.biz.{common,domain,infra,manager,mapper,service,trigger}`

### Architecture Patterns
- **分层架构**: API (接口层) → BIZ (业务层) → Manager (管理层) → Mapper (数据层)
- **服务容器模式**: `AiServiceContainer` 管理多个 AI 服务实例
- **对话记忆管理**: `ChatMemoryManager` + `DbChatMemoryStore` 实现持久化对话上下文
- **记忆隔离策略**: `memId` = 商品ID + 闲鱼买家ID,租户级别的 token 计算
- **MCP 配置**: 动态加载 MCP 服务器配置 (`McpConfigService`)

### Testing Strategy
- 使用 JUnit 进行单元测试
- 集成测试覆盖关键业务流程

### Git Workflow
- 分支策略: feature/*, bugfix/*, hotfix/*
- 主分支: master
- 提交信息: 使用中文描述变更内容

## Domain Context

### 核心业务概念
- **ChatMemoryEntity**: 单个对话上下文实体,存储 LangChain4j 对话记忆
- **MessageHistoryEntity**: 消息历史记录,持久化所有对话消息
- **商品 (Item)**: 闲鱼商品,关联 AI 对话和买家信息
- **memId**: 记忆 ID = 商品ID + 买家ID,用于隔离不同商品的对话上下文
- **AI 服务**: `ChatService`(对话)、`CustomerService`(客服)、`RAGService`(检索)、`PetDoctor`(宠物医生)、`MediaContentRecognition`(媒体识别)

### 重要设计决策
1. **记忆存储**: 使用 `DbChatMemoryStore` 从 ChatMemoryEntity 获取消息,更新时同步到 MessageHistoryEntity
2. **WebSocket 监听**: 监听 token 流,用户级别隔离,商品 ID 从消息参数中获取
3. **账号管理**: cookie 级别的账号管理,商品归属于特定账号
4. **token 计算**: 按 itemId 获取租户 ID 进行计算

## Important Constraints
- Java 21 最低版本要求
- 需要事务管理: 所有 insert 操作必须添加 `@Transactional`
- 对话上下文隔离: 严格按 memId 隔离不同商品的对话
- token 监听: 用户级别监听,商品级别隔离

## External Dependencies
- **DashScope (阿里云)**: AI 模型服务
- **OpenAI API**: GPT 模型支持
- **Nacos**: 配置中心和服务发现
- **PostgreSQL**: 关系型数据库 + PGVector 向量存储
- **闲鱼 API**: 商品和消息管理 (通过 cookie 鉴权)

## TODO 项目 (优先级)
1. 构建 TokenListener 参考 LangChain4j observability 文档,注册监听器
2. 根据账户创建商品功能
3. 消息接收机制优化 (基于 cookie 鉴权)
4. 定时任务延迟执行优化 (token 刷新)
5. Cookie 字符串创建账号功能
