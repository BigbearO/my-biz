## ADDED Requirements

### Requirement: Agent 智能体管理
系统 SHALL 提供 Agent（智能体）的完整 CRUD 管理能力。

#### Scenario: 创建智能体
- **WHEN** 用户提交智能体配置信息
- **THEN** 系统创建 Agent 记录，包含编码、名称、关联模型配置

#### Scenario: 查询智能体列表
- **WHEN** 用户查询智能体列表
- **THEN** 系统返回当前租户下的所有非删除状态智能体

#### Scenario: 更新智能体配置
- **WHEN** 用户更新智能体信息
- **THEN** 系统保存更新，关联模型配置支持动态调整

#### Scenario: 删除智能体
- **WHEN** 用户删除智能体
- **THEN** 系统执行软删除（更新 del_flag 字段）

### Requirement: ModelProvider 模型供应商管理
系统 SHALL 提供模型供应商的注册和管理能力。

#### Scenario: 注册供应商
- **WHEN** 管理员注册新的模型供应商
- **THEN** 系统保存供应商信息，包括类型、编码、名称、字段配置

#### Scenario: 查询供应商列表
- **WHEN** 用户查询模型供应商
- **THEN** 系统按模型类型分组返回供应商列表

### Requirement: ModelConfig 模型配置管理
系统 SHALL 提供模型配置的实例化管理能力。

#### Scenario: 创建模型配置
- **WHEN** 用户创建模型配置
- **THEN** 系统保存配置，支持设置是否为默认配置

#### Scenario: 启用/禁用配置
- **WHEN** 用户修改配置启用状态
- **THEN** 系统更新 is_enabled 字段

#### Scenario: 查询模型配置
- **WHEN** 用户查询某类型的模型配置
- **THEN** 系统返回该类型下所有启用和非删除状态的配置

## MODIFIED Requirements
（无修改现有需求）

## REMOVED Requirements
（无删除需求）
