## Context

为支持多智能体对话系统，需要建立一套完整的模型管理机制，包括：
- 智能体（Agent）的配置管理
- 模型供应商（ModelProvider）的注册管理
- 模型配置（ModelConfig）的多实例管理

### 约束
1. 遵循 ai-biz 模块现有分层架构
2. 使用 MyBatis-Plus 作为 ORM 框架
3. 使用 MapStruct + linpeilie 进行对象映射
4. 支持多租户隔离
5. 支持软删除

## Goals / Non-Goals

### Goals
- [x] 提供完整的 Agent CRUD 管理
- [x] 提供 ModelProvider 的注册和管理
- [x] 提供 ModelConfig 的配置管理
- [x] 支持 JSON 格式的动态配置存储
- [x] 支持模型间的逻辑关联（无物理外键）

### Non-Goals
- [ ] 模型供应商的具体实现
- [ ] 模型的动态加载和热切换
- [ ] 模型配置的版本管理

## Decisions

### 1. 外键策略：逻辑关联，无物理外键
- **决策**: Agent 中的模型标识字段（asr_model_id, tts_model_id 等）仅作为逻辑关联，不建立物理外键约束
- **理由**: 
  - 更灵活，支持跨表模型配置
  - 避免外键约束带来的级联删除复杂性
  - 便于后期扩展和模型迁移

### 2. Controller 组织：三个独立 Controller
- **决策**: 分别为 Agent、ModelProvider、ModelConfig 创建独立的 Controller
- **理由**:
  - 职责单一，接口清晰
  - 便于权限控制和模块化部署
  - 符合 RESTful 设计原则

### 3. 公共字段：统一添加租户和软删除
- **决策**: 所有实体统一添加 `tenant_id`（租户隔离）和 `del_flag`（软删除）
- **理由**:
  - 符合项目 SaaS 化设计
  - 便于数据隔离和恢复

### 4. 配置存储：JSON 字段 + TypeHandler
- **决策**: 复杂配置使用 JSON 格式存储，配合 JacksonTypeHandler
- **理由**:
  - 灵活支持不同供应商的配置差异
  - 无需频繁修改表结构

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| JSON 配置无 Schema 约束 | 配置错误难发现 | 在 Service 层增加配置校验 |
| 逻辑关联无完整性保证 | 关联数据可能不存在 | 在查询时做 LEFT JOIN 处理 |
| 租户隔离字段遗漏 | 数据安全风险 | 使用 MyBatis-Plus 自动填充 |

## Migration Plan

### 实施步骤
1. 执行数据库脚本创建表
2. 按分层顺序创建代码（Entity → BO → VO → Mapper → Manager → Controller）
3. 编译验证
4. 集成测试

### 回滚方案
- 删除新增表
- 删除新增代码文件
