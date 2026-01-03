## ADDED Requirements

### Requirement: Model Configuration Management
系统 SHALL 提供模型配置管理功能,支持配置多个 AI 模型及其参数。

#### Scenario: Add new model configuration
- **WHEN** 管理员添加新模型配置
- **THEN** 系统保存模型配置到数据库
- **AND** 模型配置包含模型名称、类型、API 密钥、最大 token 数等参数

#### Scenario: List available models
- **WHEN** 请求可用模型列表
- **THEN** 系统返回所有已配置的模型及其状态

### Requirement: Dynamic Model Selection
系统 SHALL 支持根据不同业务场景动态选择 AI 模型。

#### Scenario: Chat service uses default model
- **WHEN** ChatService 初始化
- **THEN** 系统使用配置的默认对话模型

#### Scenario: RAG service uses embedding model
- **WHEN** RAGService 执行向量检索
- **THEN** 系统使用配置的嵌入模型生成向量

#### Scenario: Switch model at runtime
- **WHEN** 请求切换对话模型
- **THEN** 系统更新当前对话使用的模型
- **AND** 后续消息使用新模型处理

### Requirement: Model Strategy Selection
系统 SHALL 提供多种模型选择策略供不同场景使用。

#### Scenario: Cost-priority strategy
- **WHEN** 选择成本优先策略
- **THEN** 系统优先选择成本最低的模型

#### Scenario: Quality-priority strategy
- **WHEN** 选择质量优先策略
- **THEN** 系统优先选择效果最好的模型

#### Scenario: Speed-priority strategy
- **WHEN** 选择速度优先策略
- **THEN** 系统优先选择响应速度最快的模型

## MODIFIED Requirements

### Requirement: AI Service Initialization
系统 SHALL 支持从配置文件或数据库加载多个 AI 模型配置,并初始化对应的 ChatModel 实例。

#### Scenario: Initialize with default model
- **WHEN** 系统启动
- **THEN** `AiServiceContainer` 初始化默认配置的模型
- **AND** 为每个服务注册对应的模型实例

#### Scenario: Switch model for specific service
- **WHEN** 更新服务的模型配置
- **THEN** 服务下次请求使用新模型
- **AND** 原模型实例保持不变,直到系统重启
