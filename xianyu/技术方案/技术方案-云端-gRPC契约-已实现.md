# 技术方案-云端-gRPC契约-已实现

> 来源：《云端技术方案.md》§六 gRPC 契约（内容重组收录，原文件保留未动）
> 关联：[技术方案-云端-架构总览](技术方案-云端-架构总览.md) ｜ [技术方案-云端-商品服务](技术方案-云端-商品服务.md)

## 一、功能与职责（总）

### 1.1 功能定位

定义云端**东西向（服务间）全部 gRPC 契约**：proto 文件清单、服务与方法、各 proto 的完整定义，以及 gRPC 状态码 → 业务错误码的统一映射。服务间除调用大模型 API（OpenAI 兼容 HTTP）外，所有东西向调用一律 gRPC。

### 1.2 职责边界

- 本文只定义**契约本身**（proto 定义与错误码映射）
- **注册 / 发现契约**（服务名、group、namespace、双实例注册）见[技术方案-云端-服务注册与发现-已实现](技术方案-云端-服务注册与发现-已实现.md)
- **调用超时预算**（客户端 90s > 网关 75s > 对话服务最坏路径 65s，逐层收窄）见[技术方案-云端-架构总览](技术方案-云端-架构总览.md) §四，本文不重复
- 各方法的业务实现见提供方文档（商品服务、模型服务、客户端元数据服务、对话服务）

### 1.3 与其他模块的关系

| proto 服务 | 提供方 | 主要消费方 |
|---|---|---|
| `item.v1.ItemService` | `goodfish-item-service`（商品服务） | 对话服务（取商品运行时配置）、admin BFF |
| `llm.v1.LlmService` | `goodfish-llm-service`（模型服务） | 对话服务（取启用模型）、admin BFF |
| `account.v1.AccountService` | `goodfish-client-meta-service`（客户端元数据服务） | 商品服务（同步落库解析账号）、admin BFF |
| `version.v1.VersionService` | `goodfish-client-meta-service`（同进程，两个 proto） | 客户端（版本检查，经网关）、admin BFF |
| `dialog.v1.DialogService` | `goodfish-ai-service`（对话服务） | 管理后台（本期未启用） |

## 二、逻辑实现（分）

### 2.1 总体设计（契约清单与共享约定）

| proto 文件 | 服务 | 方法 | 提供方 |
|---|---|---|---|
| `item/v1/item.proto` | `item.v1.ItemService` | `GetItemRuntimeConfig`、`BatchGetItemRuntimeConfig` | `goodfish-item-service` |
| `llm/v1/llm.proto` | `llm.v1.LlmService` | `GetEnabledModel`、`ReportUsage` | `goodfish-llm-service` |
| `account/v1/account.proto` | `account.v1.AccountService` | `UpsertAccount`、`GetAccount`、`GetAccountByGoodfishId`、`ListAccounts`、`UpdateAccountStatus` | `goodfish-client-meta-service` |
| `version/v1/version.proto` | `version.v1.VersionService` | `CheckUpdate` | `goodfish-client-meta-service`（**同进程，两个 proto**） |
| `dialog/v1/dialog.proto` | `dialog.v1.DialogService` | `GetConversationStats`（`TestPrompt` 已移除） | `goodfish-ai-service` |

> - proto 统一放在仓库根 `proto/` 目录，各服务通过 Maven / Go module / Python 包引用同一份定义，**避免契约漂移**
> - **契约按领域拆、部署按成本合**：`account.proto` 与 `version.proto` 由同一个 `goodfish-client-meta-service` 进程实现；将来若要拆成两个进程，**调用方无需改动**
> - **服务发现**：gRPC 调用经 Nacos 发现，注册/发现契约与 Python 侧统一约定见[技术方案-云端-服务注册与发现-已实现](技术方案-云端-服务注册与发现-已实现.md)
> - ⚠️ `dialog.v1.DialogService`（由 `goodfish-ai-service` 提供）**本期尚未启用**——当前只有「Python → Java」方向的 gRPC 调用；该服务启用时，`goodfish-ai-service` 才需要额外注册 `goodfish-ai-service-grpc`

## 四、接口设计

### 4.1 item.proto（商品服务）

```protobuf
// proto/item/v1/item.proto
syntax = "proto3";
package item.v1;

service ItemService {
  // 单商品运行时配置（对话服务主用）
  rpc GetItemRuntimeConfig(GetItemRuntimeConfigRequest) returns (ItemRuntimeConfig);
  // 批量（预留：后台批量测试 / 预热）
  rpc BatchGetItemRuntimeConfig(BatchGetItemRuntimeConfigRequest)
      returns (BatchGetItemRuntimeConfigResponse);
  // 商品列表（admin BFF 商品列表页聚合账号名用；后台单域 CRUD 仍走 REST）
  rpc ListItems(ListItemsRequest) returns (ListItemsResponse);
}

message GetItemRuntimeConfigRequest {
  string account_id = 1;   // 闲鱼账号 ID（商品按账号隔离）
  string item_id    = 2;   // 闲鱼商品 ID
}

message ItemRuntimeConfig {
  string item_id      = 1;
  string title        = 2;
  string description  = 3;
  string price        = 4;   // 用 string 避免浮点精度问题（如 199.90）
  string stock_status = 5;
  string image_url    = 6;
  PromptConfig prompt = 7;
}

message PromptConfig {
  string system_prompt    = 1;   // 系统提示词（人设 + 核心原则）
  string price_prompt     = 2;   // 议价场景
  string tech_prompt      = 3;   // 技术场景
  string default_prompt   = 4;   // 默认场景
  int32  max_reply_length = 5;   // 默认 50
  // 注：temperature / max_tokens 属模型配置，取自 LlmService.GetEnabledModel，不在此
}

message BatchGetItemRuntimeConfigRequest {
  string account_id = 1;
  repeated string item_ids = 2;
}

message BatchGetItemRuntimeConfigResponse {
  repeated ItemRuntimeConfig configs = 1;
}

// 商品列表（admin BFF 用，字段与后台列表页一致）
message ListItemsRequest {
  int32 page = 1;
  int32 page_size = 2;
  string goodfish_account_id = 3;  // 按账号筛选（可空）
  string title = 4;                // 标题模糊匹配（可空）
}
message ItemSummary {
  int64  id = 1;
  string goodfish_account_id = 2;  // 闲鱼账号 ID（BFF 据此聚合账号名/昵称）
  string goodfish_item_id = 3;
  string title = 4;
  string price = 5;                // 用 string 避免浮点精度问题
  string stock_status = 6;
  string last_sync_at = 7;         // 最后同步时间
}
message ListItemsResponse {
  repeated ItemSummary list = 1;
  int64 total = 2;
}
```

### 4.2 llm.proto（模型服务）

```protobuf
// proto/llm/v1/llm.proto
syntax = "proto3";
package llm.v1;

service LlmService {
  // 获取当前启用的模型完整配置（含解密后 API Key，仅内网）
  // 无启用模型时返回 NOT_FOUND → 对话服务映射为 50002（客户端不回复）
  rpc GetEnabledModel(GetEnabledModelRequest) returns (EnabledModel);
  // Token 用量回传（可选，用于成本核算）
  rpc ReportUsage(ReportUsageRequest) returns (ReportUsageResponse);
}

message GetEnabledModelRequest {}

message EnabledModel {
  int64  id            = 1;
  string provider_name = 2;
  string base_url      = 3;
  string api_key       = 4;   // 明文，仅内网传输，不入日志
  string model_name    = 5;   // 如 gpt-4o / qwen-max
  double temperature   = 6;
  int32  max_tokens    = 7;
}

message ReportUsageRequest {
  string item_id = 1;
  string model_name = 2;
  int32 prompt_tokens = 3;
  int32 completion_tokens = 4;
}

message ReportUsageResponse { bool success = 1; }
```

### 4.3 account.proto / version.proto / dialog.proto

```protobuf
// proto/account/v1/account.proto
syntax = "proto3";
package account.v1;

service AccountService {
  rpc UpsertAccount(UpsertAccountRequest) returns (AccountInfo);
  rpc GetAccount(GetAccountRequest) returns (AccountInfo);
  // 按闲鱼 ID 查账号（v3.3：商品同步落库时解析 account_id 用，消除跨库映射空白）
  rpc GetAccountByGoodfishId(GetAccountByGoodfishIdRequest) returns (AccountInfo);
  rpc ListAccounts(ListAccountsRequest) returns (ListAccountsResponse);
  // 停用/启用账号（admin BFF 转调；★账号只停用、不删除，停用不级联删商品）
  rpc UpdateAccountStatus(UpdateAccountStatusRequest) returns (AccountInfo);
}

message UpsertAccountRequest {
  string goodfish_id       = 1;
  string goodfish_nickname = 2;
  string remark            = 3;
}
message AccountInfo {
  int64  id = 1;
  string goodfish_id = 2;
  string goodfish_nickname = 3;
  string remark = 4;
  string last_active_at = 5;
  // 账号状态：normal / disabled。商品同步据此校验停用（40404）；
  // 注意：停用账号本方法仍正常返回（已同步商品与 AI 回复链路不受影响）
  string status = 6;
}
message GetAccountRequest { int64 id = 1; }
message GetAccountByGoodfishIdRequest { string goodfish_id = 1; }
message ListAccountsRequest { int32 page = 1; int32 page_size = 2; }
message ListAccountsResponse { repeated AccountInfo list = 1; int64 total = 2; }
message UpdateAccountStatusRequest {
  int64  id = 1;
  string status = 2;   // normal（启用）/ disabled（停用）
}
```

```protobuf
// proto/version/v1/version.proto
syntax = "proto3";
package version.v1;

service VersionService {
  rpc CheckUpdate(CheckUpdateRequest) returns (CheckUpdateResponse);
}

message CheckUpdateRequest { string current_version = 1; }

message CheckUpdateResponse {
  bool   has_update     = 1;
  string latest_version = 2;
  string release_notes  = 3;
  bool   force_update   = 4;
  string download_url   = 5;
  int64  file_size      = 6;
}
```

```protobuf
// proto/dialog/v1/dialog.proto
syntax = "proto3";
package dialog.v1;

// 供管理后台调用（统计；TestPrompt 已移除，v3.3）
service DialogService {
  rpc GetConversationStats(GetConversationStatsRequest) returns (GetConversationStatsResponse);
}

message GetConversationStatsRequest { int64 days = 1; }
message GetConversationStatsResponse { int64 conversation_count = 1; int64 message_count = 2; }
```

## 五、异常与容错

### 5.1 gRPC 状态码 → 业务错误码映射

| gRPC Status | 业务错误码 | 客户端处理 |
|---|---|---|
| `OK` | 0 | 发送回复 |
| `UNAUTHENTICATED` | 40101 | **删 token + 跳登录页**（A27，见[技术方案-云端-鉴权与授权](技术方案-云端-鉴权与授权.md)） |
| `PERMISSION_DENIED` | 40301 | **停止监听 + 冻结应用**（A42，见[技术方案-云端-鉴权与授权](技术方案-云端-鉴权与授权.md)） |
| `NOT_FOUND`（商品服务） | 40401 | 不回复（A30） |
| `NOT_FOUND`（客户端元数据服务，账号不存在） | 40403 | 商品同步整批拒绝、事务回滚，客户端提示账号未上报（见 [技术方案-云端-商品服务](技术方案-云端-商品服务.md) §4.2） |
| `NOT_FOUND`（模型服务，无启用模型）★ | 50002 | 不回复、不重试，记日志（云端配置错误，管理端需启用模型） |
| `INVALID_ARGUMENT` | 40001 | 不回复，记日志（属客户端 Bug） |
| `DEADLINE_EXCEEDED` | 50301 | 重试 3 次，仍失败进入"仅监听不回复"（A21） |
| `UNAVAILABLE` | 50301 | 同上 |
| 其他 | 50001 | 不回复（TODO：降级策略） |

### 5.2 40402（商品未配置提示词）

> 另有 **40402（商品未配置提示词）**：商品服务返回配置但 `system_prompt` 为空时由对话服务自身判定返回，不调大模型，客户端不回复。
