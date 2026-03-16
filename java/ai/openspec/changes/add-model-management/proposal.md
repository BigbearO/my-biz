# Change: 添加模型管理模块 (Agent, ModelProvider, ModelConfig)

## Why
项目需要一套完整的模型管理能力来支持多智能体系统：
- **Agent（智能体）**: 统一管理对话机器人的配置（LLM、TTS、ASR等模型关联）
- **ModelProvider（模型供应商）**: 管理不同类型的模型供应商（OpenAI、阿里云、豆包等）
- **ModelConfig（模型配置）**: 管理具体的模型配置实例，支持多租户隔离

## What Changes

### 新增实体
1. **AgentEntity** - 智能体配置实体
   - 基础信息：编码、名称、所属用户
   - 模型关联：ASR、VAD、LLM、VLLM、TTS、Memory、Intent 模型标识
   - 语音配置：音色标识、语言编码
   - 对话配置：聊天记录配置、角色设定、总结记忆
   - 公共字段：租户ID、软删除标志、审计字段

2. **ModelProviderEntity** - 模型供应商实体
   - 模型类型（Memory/ASR/VAD/LLM/TTS）
   - 供应商编码和名称
   - 供应商字段配置（JSON格式）
   - 公共字段：租户ID、软删除标志、审计字段

3. **ModelConfigEntity** - 模型配置实体
   - 模型类型、编码、名称
   - 是否默认配置、是否启用
   - 模型详细配置（JSON格式）
   - 官方文档链接、备注
   - 公共字段：租户ID、软删除标志、审计字段

### 代码分层
遵循 ai-biz 模块现有分层规范：
- `domain/entity/` - Entity 实体类
- `domain/bo/` - BO 业务对象（继承 BaseEntity，@AutoMapper）
- `domain/vo/` - VO 视图对象（实现 Serializable，@AutoMapper）
- `infra/mapper/` - Mapper 接口（继承 BaseMapper）
- `infra/manager/` - Manager 类（继承 MPJBaseServiceImpl）
- `trigger/api/` - Controller（@RestController）

### 数据库
- 新建表：`ai_agent`、`ai_model_provider`、`ai_model_config`
- 位置：`db/model.sql`

### API 设计
三个独立 Controller：
- `AgentController` - 智能体管理接口
- `ModelProviderController` - 模型供应商管理接口
- `ModelConfigController` - 模型配置管理接口

## Impact
- **新增模块**: ai-biz 新增模型管理功能
- **数据库**: 新增 3 张表
- **API**: 新增 3 组 RESTful 接口
- **关联关系**: Agent 与 ModelConfig 逻辑关联（无外键约束）
