# LLM 模块管理技术方案

> 版本：v1.1 ｜ 日期：2026-09-29 ｜ 状态：技术设计
> 关联文档：《云端需求.md》3.4 模型管理、《云端技术方案.md》4.4/4.5
> 实现模块：`backend/llm-manage`（Go + Kratos 微服务）

---

## 一、模块概述

LLM 模块管理负责大模型供应商和模型的配置管理，包括：
- 供应商 CRUD（名称、接口地址、API Key、状态）
- 模型 CRUD（所属供应商、显示名、实际模型名、温度、最大输出、状态）
- 供应商连接测试
- 全局启用规则（同一时间只能有一个模型启用）

本模块是独立的 Go 微服务，通过 gRPC/REST 对外提供接口，供 RuoYi 管理后台调用。

---

## 二、技术栈

| 层级 | 选型 |
|------|------|
| 微服务框架 | Kratos v2 |
| 语言 | Go 1.21+ |
| ORM | GORM（PostgreSQL） |
| 缓存 | Redis |
| 接口协议 | gRPC + REST（HTTP） |
| 接口定义 | Protocol Buffers v3 |
| 代码生成 | gow（go-wind-toolkit） |
| 依赖注入 | 手写 wiring.go + register 锚点 |
| 配置管理 | 多 yaml 文件（data/server/auth/logger/client） |

---

## 三、目录结构

在现有 `llm-manage/app/admin/service` 基础上新增以下文件：

```
llm-manage/
├── api/protos/admin/service/v1/
│   ├── llm_provider.proto          # 供应商接口定义
│   └── llm_model.proto             # 模型接口定义
├── api/gen/go/admin/service/v1/    # 生成的 Go 代码
└── app/admin/service/
    └── internal/
        ├── data/
        │   ├── llm_provider_repo.go    # 供应商仓储
        │   ├── llm_model_repo.go       # 模型仓储
        │   └── models/                  # GORM 模型
        │       ├── llm_provider.go
        │       └── llm_model.go
        ├── service/
        │   ├── llm_provider_service.go  # 供应商服务
        │   └── llm_model_service.go     # 模型服务
        └── server/
            ├── rest_server.go           # REST 路由注册（修改）
            └── grpc_server.go           # gRPC 服务注册（修改）
```

---

## 四、数据库表设计

### 4.1 llm_provider（大模型供应商表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键，自增 |
| name | VARCHAR(64) | 供应商名称（如 OpenAI、DeepSeek、通义千问） |
| base_url | VARCHAR(256) | 接口地址 |
| api_key | VARCHAR(256) | API 密钥（加密存储） |
| status | SMALLINT | 状态：1=启用，0=停用 |
| created_at | TIMESTAMPTZ | 创建时间 |
| updated_at | TIMESTAMPTZ | 更新时间 |
| deleted_at | TIMESTAMPTZ | 软删除时间（GORM 软删除） |

**索引**：
- `idx_llm_provider_name`：name 普通索引
- `idx_llm_provider_status`：status 普通索引

### 4.2 llm_model（大模型表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键，自增 |
| provider_id | BIGINT | 关联 llm_provider.id |
| display_name | VARCHAR(64) | 模型显示名称（如 GPT-4o） |
| model_name | VARCHAR(64) | 实际模型名（如 gpt-4o） |
| temperature | FLOAT | 温度参数，默认 0.7 |
| max_tokens | INTEGER | 最大输出 token 数，默认 2048 |
| status | SMALLINT | 状态：1=启用，0=停用 |
| created_at | TIMESTAMPTZ | 创建时间 |
| updated_at | TIMESTAMPTZ | 更新时间 |
| deleted_at | TIMESTAMPTZ | 软删除时间（GORM 软删除） |

**索引**：
- `idx_llm_model_provider_id`：provider_id 普通索引
- `idx_llm_model_status`：status 普通索引

---

## 五、Proto 接口设计

### 5.1 llm_provider.proto

```protobuf
syntax = "proto3";

package admin.service.v1;

import "google/api/annotations.proto";
import "google/protobuf/empty.proto";

// 供应商服务
service LlmProvider {
  // 供应商列表（分页）
  rpc List (ListLlmProviderRequest) returns (ListLlmProviderReply) {
    option (google.api.http) = {
      get: "/llm/provider/list"
    };
  }
  // 供应商详情
  rpc Get (GetLlmProviderRequest) returns (LlmProviderInfo) {
    option (google.api.http) = {
      get: "/llm/provider/{id}"
    };
  }
  // 新增供应商
  rpc Create (CreateLlmProviderRequest) returns (LlmProviderInfo) {
    option (google.api.http) = {
      post: "/llm/provider"
      body: "*"
    };
  }
  // 更新供应商
  rpc Update (UpdateLlmProviderRequest) returns (LlmProviderInfo) {
    option (google.api.http) = {
      put: "/llm/provider"
      body: "*"
    };
  }
  // 删除供应商
  rpc Delete (DeleteLlmProviderRequest) returns (google.protobuf.Empty) {
    option (google.api.http) = {
      delete: "/llm/provider/{id}"
    };
  }
  // 测试连接
  rpc TestConnection (TestConnectionRequest) returns (TestConnectionReply) {
    option (google.api.http) = {
      post: "/llm/provider/test"
      body: "*"
    };
  }
}

// 供应商信息
message LlmProviderInfo {
  int64 id = 1;
  string name = 2;
  string base_url = 3;
  string api_key = 4;  // 返回时脱敏
  int32 status = 5;
  string created_at = 6;
  string updated_at = 7;
}

// 列表请求
message ListLlmProviderRequest {
  int32 page = 1;
  int32 page_size = 2;
  string name = 3;  // 模糊搜索
  int32 status = 4;
}

// 列表回复
message ListLlmProviderReply {
  repeated LlmProviderInfo list = 1;
  int64 total = 2;
}

// 详情请求
message GetLlmProviderRequest {
  int64 id = 1;
}

// 新增请求
message CreateLlmProviderRequest {
  string name = 1;
  string base_url = 2;
  string api_key = 3;
  int32 status = 4;
}

// 更新请求
message UpdateLlmProviderRequest {
  int64 id = 1;
  string name = 2;
  string base_url = 3;
  string api_key = 4;  // 为空则不更新
  int32 status = 5;
}

// 删除请求
message DeleteLlmProviderRequest {
  int64 id = 1;
}

// 测试连接请求
message TestConnectionRequest {
  int64 id = 1;  // 传 id 则用已保存的配置
  string base_url = 2;  // 不传 id 则用传入的配置
  string api_key = 3;
}

// 测试连接回复
message TestConnectionReply {
  bool success = 1;
  string message = 2;
  int64 latency_ms = 3;  // 响应耗时
}
```

### 5.2 llm_model.proto

```protobuf
syntax = "proto3";

package admin.service.v1;

import "google/api/annotations.proto";
import "google/protobuf/empty.proto";

// 模型服务
service LlmModel {
  // 模型列表（分页）
  rpc List (ListLlmModelRequest) returns (ListLlmModelReply) {
    option (google.api.http) = {
      get: "/llm/model/list"
    };
  }
  // 模型详情
  rpc Get (GetLlmModelRequest) returns (LlmModelInfo) {
    option (google.api.http) = {
      get: "/llm/model/{id}"
    };
  }
  // 新增模型
  rpc Create (CreateLlmModelRequest) returns (LlmModelInfo) {
    option (google.api.http) = {
      post: "/llm/model"
      body: "*"
    };
  }
  // 更新模型
  rpc Update (UpdateLlmModelRequest) returns (LlmModelInfo) {
    option (google.api.http) = {
      put: "/llm/model"
      body: "*"
    };
  }
  // 删除模型
  rpc Delete (DeleteLlmModelRequest) returns (google.protobuf.Empty) {
    option (google.api.http) = {
      delete: "/llm/model/{id}"
    };
  }
  // 启用模型（自动停用其他）
  rpc Enable (EnableLlmModelRequest) returns (LlmModelInfo) {
    option (google.api.http) = {
      put: "/llm/model/enable/{id}"
    };
  }
}

// 模型信息
message LlmModelInfo {
  int64 id = 1;
  int64 provider_id = 2;
  string provider_name = 3;  // 关联查询返回
  string display_name = 4;
  string model_name = 5;
  float temperature = 6;
  int32 max_tokens = 7;
  int32 status = 8;
  string created_at = 9;
  string updated_at = 10;
}

// 列表请求
message ListLlmModelRequest {
  int32 page = 1;
  int32 page_size = 2;
  int64 provider_id = 3;
  string display_name = 4;  // 模糊搜索
  int32 status = 5;
}

// 列表回复
message ListLlmModelReply {
  repeated LlmModelInfo list = 1;
  int64 total = 2;
}

// 详情请求
message GetLlmModelRequest {
  int64 id = 1;
}

// 新增请求
message CreateLlmModelRequest {
  int64 provider_id = 1;
  string display_name = 2;
  string model_name = 3;
  float temperature = 4;
  int32 max_tokens = 5;
  int32 status = 6;
}

// 更新请求
message UpdateLlmModelRequest {
  int64 id = 1;
  int64 provider_id = 2;
  string display_name = 3;
  string model_name = 4;
  float temperature = 5;
  int32 max_tokens = 6;
  int32 status = 7;
}

// 删除请求
message DeleteLlmModelRequest {
  int64 id = 1;
}

// 启用请求
message EnableLlmModelRequest {
  int64 id = 1;
}
```

---

## 六、Service 层设计

### 6.1 LlmProviderService

| 方法 | 说明 | 关键逻辑 |
|------|------|----------|
| List | 分页查询供应商列表 | 支持名称模糊搜索、状态筛选 |
| Get | 获取供应商详情 | api_key 返回时脱敏（只显示前4后4） |
| Create | 新增供应商 | api_key 加密存储 |
| Update | 更新供应商 | api_key 为空则不更新 |
| Delete | 删除供应商 | 软删除；有关联模型时禁止删除 |
| TestConnection | 测试连接 | 调 OpenAI 兼容接口 `/models` 或发一条轻量请求 |

### 6.2 LlmModelService

| 方法 | 说明 | 关键逻辑 |
|------|------|----------|
| List | 分页查询模型列表 | 关联查询供应商名称 |
| Get | 获取模型详情 | 关联查询供应商信息 |
| Create | 新增模型 | 校验 provider_id 存在 |
| Update | 更新模型 | 校验 provider_id 存在 |
| Delete | 删除模型 | 软删除；启用中的模型禁止删除 |
| Enable | 启用模型 | 事务：先把所有模型设为停用，再把当前模型设为启用 |

---

## 七、Data 层设计

### 7.1 GORM 模型

```go
// LlmProvider 供应商模型
type LlmProvider struct {
    ID        int64     `gorm:"primaryKey;autoIncrement"`
    Name      string    `gorm:"size:64;not null;index"`
    BaseURL   string    `gorm:"column:base_url;size:256;not null"`
    APIKey    string    `gorm:"column:api_key;size:256;not null"`
    Status    int32     `gorm:"not null;default:1;index"`
    CreatedAt time.Time `gorm:"autoCreateTime"`
    UpdatedAt time.Time `gorm:"autoUpdateTime"`
    DeletedAt gorm.DeletedAt `gorm:"index"`
}

func (LlmProvider) TableName() string {
    return "llm_provider"
}

// LlmModel 模型
type LlmModel struct {
    ID          int64     `gorm:"primaryKey;autoIncrement"`
    ProviderID  int64     `gorm:"column:provider_id;not null;index"`
    DisplayName string    `gorm:"column:display_name;size:64;not null"`
    ModelName   string    `gorm:"column:model_name;size:64;not null"`
    Temperature float64   `gorm:"not null;default:0.7"`
    MaxTokens   int32     `gorm:"column:max_tokens;not null;default:2048"`
    Status      int32     `gorm:"not null;default:0;index"`
    CreatedAt   time.Time `gorm:"autoCreateTime"`
    UpdatedAt   time.Time `gorm:"autoUpdateTime"`
    DeletedAt   gorm.DeletedAt `gorm:"index"`
}

func (LlmModel) TableName() string {
    return "llm_model"
}
```

### 7.2 API Key 加密

- 使用 AES-256-GCM 加密存储
- 加密密钥从配置文件读取（`data.yaml` 新增 `data.encrypt_key`）
- 数据库中存的是密文，使用时解密
- 返回给前端时脱敏（如 `sk-****abcd`）

### 7.3 全局启用保证

**应用层保证**：Enable 方法用事务，先全量停用再启用当前。

> 说明：当前版本暂时只支持一个模型启用，后续可能扩展为多个模型同时启用，因此不做数据库层面的唯一索引约束。

---

## 八、测试连接功能

### 8.1 实现方式

- 调用 OpenAI 兼容接口的 `/v1/models` 端点
- 或发一条 `max_tokens=1` 的轻量 chat 请求
- 记录响应耗时

### 8.2 超时设置

- HTTP 客户端超时：10 秒
- 超过 10 秒视为连接失败

### 8.3 返回结果

```json
{
  "success": true,
  "message": "连接成功",
  "latency_ms": 320
}
```

---

## 九、装配注册

按照项目约定，在 `wiring.go` 的锚点后注册：

```go
// ── register:repo ──
llmProviderRepo := data.NewLlmProviderRepo(ctx)
llmModelRepo := data.NewLlmModelRepo(ctx)

// ── register:service ──
llmProviderService := service.NewLlmProviderService(ctx, llmProviderRepo)
llmModelService := service.NewLlmModelService(ctx, llmModelRepo, llmProviderRepo)

// register:rest-arg / register:grpc-arg
llmProviderService, llmModelService,
```

在 `rest_server.go` 和 `grpc_server.go` 的 `register:route` 后注册路由。

---

## 十、开发步骤

### 10.1 数据库初始化

```sql
-- 创建表（GORM AutoMigrate 或手动执行）
CREATE TABLE llm_provider (...);
CREATE TABLE llm_model (...);
```

### 10.2 代码生成顺序

1. 编写 Proto 文件（`llm_provider.proto`、`llm_model.proto`）
2. 执行 `make api` 生成 Go 代码和 OpenAPI 文档
3. 编写 GORM 模型（`internal/data/models/`）
4. 编写 Repo 层（`internal/data/llm_*_repo.go`）
5. 编写 Service 层（`internal/service/llm_*_service.go`）
6. 在 `wiring.go` 注册仓储和服务
7. 在 `rest_server.go` / `grpc_server.go` 注册路由
8. 编写单元测试
9. 启动服务，用 Swagger 测试接口

### 10.3 配置更新

在 `data.yaml` 新增加密密钥配置：

```yaml
data:
  encrypt_key: "your-32-byte-encryption-key-here"
```

---

## 十一、接口清单汇总

### REST 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /llm/provider/list | GET | 供应商列表 |
| /llm/provider/{id} | GET | 供应商详情 |
| /llm/provider | POST | 新增供应商 |
| /llm/provider | PUT | 更新供应商 |
| /llm/provider/{id} | DELETE | 删除供应商 |
| /llm/provider/test | POST | 测试连接 |
| /llm/model/list | GET | 模型列表 |
| /llm/model/{id} | GET | 模型详情 |
| /llm/model | POST | 新增模型 |
| /llm/model | PUT | 更新模型 |
| /llm/model/{id} | DELETE | 删除模型 |
| /llm/model/enable/{id} | PUT | 启用模型 |

### gRPC 接口

对应 Proto 定义的 `LlmProvider` 和 `LlmModel` 服务，方法名与 REST 一致。
