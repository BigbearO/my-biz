# AGENTS.md
 


这是最高指令
1. 所有后端服务代码，客户端代码，都在当前目录中实现，backend和client目录，
2. 管理页面ui在E:\project\ruoyi\plus-ui这儿实现
3. 本项目基于E:\project\ruoyi\RuoYi-Cloud-Plus 这个作为登录注册和权限管理
4. 你做决策之前先做解释，再递交审核
5. 排查问题时，先解释，再给方案别一上来就改
6. github相关搜索加上前缀https://v6.gh-proxy.org/
7. 代码写完,编译，自测之前，先问问要不要编译测试,不要直接就执行

maven在 
E:\programing\apache\apache-maven-3.9.9\conf\ 


## 一、代码分层规范

### 1.1 分层架构

项目采用 **repo → manager → service** 三层架构：

| 层 | 目录 | 职责 | 调用谁 | 被谁调用 |
|---|---|---|---|---|
| repo | `internal/data/` | 纯数据库操作，单表 CRUD，无业务逻辑 | 数据库 | manager |
| manager | `internal/manager/` | 基础设施层，原子方法，封装可复用的基础操作（加密/解密/事务/关联查询等） | repo | service |
| service | `internal/service/` | 业务编排层，组合多个 manager 完成完整业务流程 | manager | 传输层（server） |

### 1.2 各层职责边界

**repo 层**：
- 只做单表的增删改查
- 不包含业务逻辑
- 不做数据加密/解密
- 不做事务编排（事务由 manager 层控制）

**manager 层**：
- 提供**原子方法**，每个方法是一个不可再分的基础操作
- 可以调用多个 repo 完成一个原子操作（如事务内多表操作）
- 可以包含基础设施逻辑（加密、解密、数据转换、关联查询等）
- 方法应该是**可复用**的，能被多个 service 调用
- 不做面向接口的业务编排（如参数校验、错误组装、返回值封装）

**service 层**：
- 面向接口的完整业务流程
- 负责参数校验、业务规则判断、错误处理
- 调用多个 manager 进行业务编排
- 组装返回值
- 不直接操作数据库（不直接调用 repo）

### 1.3 调用关系

```
server（传输层）
    ↓
service（业务编排）
    ↓
manager（原子操作/基础设施）
    ↓
repo（数据访问）
    ↓
database
```

**禁止**：
- service 直接调用 repo（必须经过 manager）
- manager 调用 service（依赖方向只能从上到下）
- repo 调用 manager 或 service

### 1.4 示例

以"创建供应商"为例：

```
LlmProviderService.Create（service：业务编排）
  ├─ 参数校验（名称、地址、API Key 非空）
  ├─ LlmProviderManager.CreateWithEncryptedKey（manager：原子操作）
  │    ├─ Encryptor.Encrypt（加密 API Key）
  │    └─ LlmProviderRepo.Create（存库）
  └─ 组装返回值（脱敏 API Key）
```

以"启用模型"为例：

```
LlmModelService.Enable（service：业务编排）
  ├─ 校验模型存在
  ├─ LlmModelManager.EnableWithTransaction（manager：原子操作）
  │    ├─ 事务开始
  │    ├─ LlmModelRepo 批量停用所有启用模型
  │    ├─ LlmModelRepo 启用当前模型
  │    ├─ 事务提交
  │    └─ 查询当前模型详情
  └─ 组装返回值（关联供应商名称）
```

---

## 二、禁止魔法数字和魔法字符串

### 2.1 定义

**魔法数字**：代码中直接出现的、没有命名的数字字面量（如 `1`、`0`、`10`、`0.7`、`2048`）。

**魔法字符串**：代码中直接出现的、没有命名的字符串字面量（如 `"llm_provider/service/admin-service"`、`"供应商不存在"`）。

### 2.2 规则

- **所有**业务含义的数字和字符串必须提取为命名常量或变量
- 常量统一放在 `internal/constants/` 包中
- 错误消息定义为错误变量（`errors.New`），方便统一管理和后续国际化
- 日志名称定义为包级变量，方便统一修改
- 只有纯技术含义的字面量可以例外（如循环索引 `0`、字符串拼接的空字符串 `""`），但建议尽量也命名

### 2.3 常量分类

```go
// 状态常量
const (
    StatusEnabled  = 1
    StatusDisabled = 0
)

// 分页默认值
const (
    DefaultPage     = 1
    DefaultPageSize = 10
)

// 模型默认参数
const (
    DefaultTemperature = 0.7
    DefaultMaxTokens   = 2048
)

// 日志名称（变量，因为可能需要拼接）
var (
    LoggerNameLlmProviderService = "llm_provider/service/admin-service"
    LoggerNameLlmModelService    = "llm_model/service/admin-service"
)

// 错误变量
var (
    ErrProviderNotFound  = errors.New("供应商不存在")
    ErrModelNotFound     = errors.New("模型不存在")
)
```

### 2.4 反例（禁止）

```go
// 魔法数字
if model.Status == 1 { ... }
page := 1
pageSize := 10
temperature := 0.7

// 魔法字符串
log: ctx.NewLoggerHelper("llm_provider/service/admin-service")
return nil, fmt.Errorf("供应商不存在")
```

### 2.5 正例（要求）

```go
// 使用常量
if model.Status == constants.StatusEnabled { ... }
page := constants.DefaultPage
pageSize := constants.DefaultPageSize
temperature := constants.DefaultTemperature

// 使用变量
log: ctx.NewLoggerHelper(constants.LoggerNameLlmProviderService)
return nil, constants.ErrProviderNotFound
```

---

## 三、其他约定

- 新增代码必须遵守以上准则
- 修改旧代码时，应顺便将涉及的魔法值提取为常量
- 代码评审时，以上准则作为必查项
