# gRPC 公共模块接入与代码生成指南

本文说明 `goodfish-ai-service` 如何安装 `xxs-nacos-grpc` 公共模块、接入 Nacos gRPC 服务发现，并基于现有 proto 生成 Python 调用代码。

## 1. 模块定位

### 1.1 公共模块

公共模块位于：

```text
backend/python/xxs-nacos-grpc
```

导入名：

```python
import xxs_nacos_grpc
```

公共模块负责：

| 能力 | 说明 |
|---|---|
| gRPC 配置解析 | 解析 `grpc.client`、`grpc.discovery`、`grpc.channel` |
| Nacos 实例发现 | 按服务名订阅实例变更 |
| 健康实例过滤 | 只保留 `enabled`、`healthy` 且权重大于 0 的实例 |
| channel 管理 | 实例变更去抖、原子替换、旧 channel 延迟关闭 |
| 负载均衡 | 通过 gRPC 内置 `round_robin` 或 `pick_first` 实现 |
| 定兜底对账 | 周期性重新查询健康实例，防止订阅推送丢失 |
| 身份透传 | HTTP 头转 `ContextVar`，再写入 gRPC metadata |

公共模块不包含业务 proto，不依赖 FastAPI，后续可以被其他 Python 服务复用。

### 1.2 业务侧代码

业务服务位于：

```text
backend/goodfish-ai-service
```

当前已完成：

| 位置 | 职责 |
|---|---|
| `app/main.py` | FastAPI lifespan 中初始化和关闭 gRPC registry |
| `app/nacos/manager.py` | 复用现有 Nacos naming client，提供实例订阅和健康实例查询 |
| `app/grpc/service_client.py` | 通用 gRPC 调用封装 |
| `app/grpc/llm_model.py` | `LlmModel` 业务客户端 |
| `admin/service/v1/*_pb2*.py` | 从现有 proto 生成的 Python stub |

## 2. 安装公共模块

以下命令均在 CMD 中执行。

### 2.1 进入服务目录

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
```

### 2.2 安装运行和生成依赖

服务使用：

```text
D:\xhf\project\test\my-biz\xianyu\backend\.venv
```

安装依赖：

```cmd
..\.venv\Scripts\python.exe -m pip install --upgrade pip setuptools wheel
..\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

`requirements.txt` 已包含本地 editable 依赖：

```text
-e ../python/xxs-nacos-grpc
```

因此安装依赖时会同时安装公共模块。

### 2.3 验证公共模块

```cmd
..\.venv\Scripts\python.exe -c "import grpc, xxs_nacos_grpc; print('grpc=', grpc.__version__); print('xxs_nacos_grpc ok')"
```

期望输出包含：

```text
xxs_nacos_grpc ok
```

## 3. Nacos 与配置接入

### 3.1 下游注册契约

本服务是 gRPC 客户端，只订阅下游 gRPC 服务，不注册自己的 `-grpc` 服务。

下游服务必须满足：

| 项 | 要求 |
|---|---|
| namespace | 与 `.env` 中 `NACOS_NAMESPACE_ID` 完全一致 |
| group | 与 `.env` 中 `NACOS_GROUP` 完全一致 |
| 服务名 | 与配置中的值完全一致，例如 `goodfish-llm-service-grpc` |
| 实例端口 | 下游真实 gRPC 端口，不是 HTTP 端口 |
| 实例状态 | `enabled=true`、`healthy=true` |

### 3.2 配置示例

本地配置：

```text
config/application.yaml
```

示例：

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
    debounce_ms: 1500
    close_grace_seconds: 30
    reconcile_interval_seconds: 60

  channel:
    lb_policy: round_robin
    keepalive_time_ms: 30000
    keepalive_timeout_ms: 10000
    keepalive_permit_without_calls: 1
    max_message_mb: 16

  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 3000
```

配置含义：

| 配置 | 说明 |
|---|---|
| `discovery.services` | alias 到 Nacos gRPC 服务名的映射 |
| `discovery.debounce_ms` | 实例变更去抖时间 |
| `discovery.close_grace_seconds` | 旧 channel 延迟关闭时间，保护在途请求 |
| `discovery.reconcile_interval_seconds` | 兜底重查间隔，`0` 表示关闭 |
| `channel.lb_policy` | `round_robin` 或 `pick_first` |
| `client.client_id` | 写入 gRPC metadata 的 `x-client-id` |
| `client.default_timeout_ms` | 默认 gRPC 调用超时 |

如果 `NACOS_DATA_ID` 不为空，Nacos 配置会与本地配置深度合并，同 key 时 Nacos 覆盖本地。

### 3.3 配置来源与优先级

gRPC 配置只有一份结构，来源分为三层：

| 优先级 | 来源 | 说明 |
|---|---|---|
| 低 | 公共包代码默认值 | 未配置时使用默认值 |
| 中 | 本地 `config/application.yaml` | 服务启动的基础配置 |
| 高 | Nacos 配置文件 | `NACOS_DATA_ID` 非空时拉取并覆盖同 key 配置 |

合并规则：

1. 本地配置和 Nacos 配置取深度并集。
2. 同名叶子节点以 Nacos 配置为准。
3. `NACOS_DATA_ID` 为空时不创建配置客户端，只使用本地配置。
4. 本地文件修改不会自动生效，需要重启服务。
5. Nacos 配置变更会触发热更新。
6. 公共包对 `grpc` 下未知字段报错，避免拼写错误被忽略。

示例：

```yaml
# config/application.yaml
grpc:
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 3000
  channel:
    max_message_mb: 16
```

Nacos 中只想覆盖超时时间：

```yaml
grpc:
  client:
    default_timeout_ms: 1000
```

最终结果：

```yaml
grpc:
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 1000
  channel:
    max_message_mb: 16
```

注意：`services` 也是深度合并。本地已配置的 alias 不会被 Nacos 省略动作删除；如果要彻底移除某个本地 alias，需要同步修改本地配置。

### 3.4 gRPC 配置字段说明

#### `grpc.discovery.services`

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
      item: goodfish-item-service-grpc
```

| 项 | 说明 |
|---|---|
| key | 服务别名，业务代码通过 alias 获取 stub |
| value | 下游在 Nacos 中注册的 gRPC 服务名 |

约定：

| 约定 | 说明 |
|---|---|
| alias | 服务内部调用名，稳定后不建议随意修改 |
| value | 必须与 Nacos 服务名一字不差 |
| 后缀 | 下游 gRPC 服务名建议使用 `-grpc` 后缀 |
| 多下游 | 每个下游配置一个映射项 |

`LlmModelClient` 当前固定使用 alias：

```text
llm-model
```

如果修改 alias，需要同步修改业务代码中的常量。

#### `grpc.discovery` 调度参数

| 配置 | 类型 | 默认值 | 说明 |
|---|---:|---:|---|
| `debounce_ms` | int | `1500` | Nacos 连续推送时的去抖间隔，避免频繁重建 channel |
| `close_grace_seconds` | int | `30` | 旧 channel 延迟关闭秒数，保护在途 RPC |
| `reconcile_interval_seconds` | int | `60` | 兜底重查健康实例间隔，`0` 表示关闭 |

推荐：

| 场景 | 建议配置 |
|---|---|
| 本地开发 | `debounce_ms: 300`，`reconcile_interval_seconds: 10` |
| 测试环境 | 使用默认值 |
| 生产环境 | 使用默认值，不建议把去抖调得太小 |

#### `grpc.channel`

| 配置 | 类型 | 默认值 | 可选值 | 说明 |
|---|---:|---:|---|---|
| `lb_policy` | str | `round_robin` | `round_robin`、`pick_first` | 负载均衡策略 |
| `keepalive_time_ms` | int | `30000` | 正整数 | keepalive 发送间隔 |
| `keepalive_timeout_ms` | int | `10000` | 正整数 | keepalive 超时时间 |
| `keepalive_permit_without_calls` | int | `1` | `0`、`1` | 无活跃调用时是否允许 keepalive |
| `max_message_mb` | int | `16` | 正整数 | 单条消息最大大小，单位 MB |
| `health_check` | bool | `false` | `true`、`false` | 是否启用 gRPC health check 配置 |

说明：

1. `round_robin` 适合一个下游多实例的场景。
2. `pick_first` 会优先使用第一个可用地址，适合本地调试或单实例场景。
3. `health_check: true` 要求下游实现标准 `grpc.health.v1.Health` 服务；未实现前保持 `false`。

#### `grpc.client`

| 配置 | 类型 | 默认值 | 说明 |
|---|---:|---:|---|
| `client_id` | str | 空字符串 | 写入 gRPC metadata 的 `x-client-id` |
| `default_timeout_ms` | int | `3000` | 默认 RPC 超时时间 |

建议始终配置：

```yaml
grpc:
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 3000
```

如果 `client_id` 为空，公共模块不会发送 `x-client-id` metadata。

### 3.5 与 Nacos 连接相关的环境变量

gRPC 下游发现复用当前服务的 Nacos naming client。以下 `.env` 配置会同时影响 HTTP 注册和 gRPC 下游发现：

| 环境变量 | 影响 |
|---|---|
| `NACOS_SERVER_ADDR` | Nacos 地址 |
| `NACOS_NAMESPACE_ID` | HTTP 注册和 gRPC 下游发现所在 namespace |
| `NACOS_GROUP` | HTTP 注册和 gRPC 下游发现所在 group |
| `NACOS_CLUSTER_NAME` | 当前服务注册使用的 cluster |
| `NACOS_DATA_ID` | Nacos 远程配置 Data ID，为空则只用本地配置 |
| `NACOS_CONFIG_FORMAT` | Nacos 配置格式，`yaml` 或 `properties` |

gRPC 服务名本身不单独配置 namespace 和 group。下游必须注册在当前服务使用的同一个 namespace 和 group 下。

### 3.6 环境变量占位符

配置值支持递归展开进程环境变量：

```yaml
grpc:
  client:
    client_id: ${GRPC_CLIENT_ID}
    default_timeout_ms: ${GRPC_DEFAULT_TIMEOUT_MS}
```

CMD 示例：

```cmd
set GRPC_CLIENT_ID=goodfish-ai-service
set GRPC_DEFAULT_TIMEOUT_MS=2000
```

注意：

| 事项 | 说明 |
|---|---|
| 未设置 | 占位符原样保留 |
| 数字字段 | 占位符对应环境变量必须能转为数字 |
| 敏感值 | 可以用环境变量注入，但不要打印日志 |
| 启动前设置 | 环境变量必须在服务进程启动前存在 |

### 3.7 常用配置场景

#### 场景一：暂不调用 gRPC 下游

可以不配置 `grpc` 节点，或配置为空服务列表：

```yaml
grpc:
  discovery:
    services: {}
```

服务仍会注册 HTTP 实例，只是不创建下游 gRPC channel。

#### 场景二：只用本地配置

`.env`：

```env
NACOS_DATA_ID=
```

`config/application.yaml`：

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 3000
```

#### 场景三：本地保留默认值，Nacos 管理下游服务

本地：

```yaml
grpc:
  channel:
    lb_policy: round_robin
    max_message_mb: 16
```

Nacos：

```yaml
grpc:
  discovery:
    services:
      item: goodfish-item-service-grpc
      llm-model: goodfish-llm-service-grpc
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 2000
```

#### 场景四：本地调试单实例

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
    debounce_ms: 300
    reconcile_interval_seconds: 10
  channel:
    lb_policy: pick_first
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 2000
```

### 3.8 配置热更新行为

Nacos 配置变更后：

| 变更 | 行为 |
|---|---|
| 新增 alias | 订阅新的下游服务并创建 channel |
| 移除 alias | 关闭对应 channel |
| 服务名变化 | 关闭旧 channel，订阅新服务名 |
| channel 参数变化 | 下次实例刷新时重建 channel |
| 超时时间变化 | 新 RPC 使用新超时时间 |
| 去抖、兜底参数变化 | 新刷新使用新参数 |
| 配置非法 | 拒绝本次远程配置，继续使用旧配置 |

在途 RPC 不主动取消，旧 channel 会按 `close_grace_seconds` 延迟关闭。

## 4. FastAPI 生命周期

启动顺序：

```text
1. 加载本地配置
2. 拉取并合并 Nacos 配置
3. 校验 gRPC 配置
4. 创建 GrpcChannelRegistry
5. 订阅下游 gRPC 服务并创建 channel
6. 注册自身 HTTP 实例
7. 挂载配置变更监听
8. 开始对外提供 HTTP 服务
```

关闭顺序：

```text
1. 注销自身 HTTP 实例
2. 关闭所有 gRPC channel
3. 关闭 Nacos client
```

运行时对象挂载在：

```python
request.app.state.grpc_registry
```

## 5. 身份透传

HTTP 入口支持以下请求头：

| HTTP 头 | gRPC metadata |
|---|---|
| `X-User-Id` | `x-user-id` |
| `X-User-Info` | `x-user-info` |
| `Authorization` | `authorization` |

公共模块额外写入：

```text
x-client-id: goodfish-ai-service
```

取值来自：

```yaml
grpc:
  client:
    client_id: goodfish-ai-service
```

注意：

| 事项 | 要求 |
|---|---|
| metadata key | gRPC 侧统一使用小写 |
| 非 ASCII | 当前实现要求身份值为 ASCII |
| 请求隔离 | 每个请求结束后重置 `ContextVar`，避免并发串号 |
| 敏感信息 | 不要打印 `authorization` 和完整用户信息日志 |

## 6. 生成 LlmModel Python stub

### 6.1 proto 来源

使用现有模型管理 proto：

```text
D:\xhf\project\test\my-biz\xianyu\backend\llm-manage\api\protos\admin\service\v1\llm_model.proto
```

该 proto 定义：

| 项 | 值 |
|---|---|
| package | `admin.service.v1` |
| service | `LlmModel` |
| client stub | `LlmModelStub` |

### 6.2 CMD 直接生成

如果本机不允许执行 PowerShell 脚本，使用以下 CMD 命令。

先进入服务目录：

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
```

执行生成：

```cmd
D:\xhf\project\test\my-biz\xianyu\backend\.venv\Scripts\python.exe -m grpc_tools.protoc --proto_path=D:\xhf\project\test\my-biz\xianyu\backend\llm-manage\api\protos --proto_path=D:\xhf\project\test\my-biz\xianyu\backend\.venv\Lib\site-packages --proto_path=D:\xhf\project\test\my-biz\xianyu\backend\.venv\Lib\site-packages\grpc_tools\_proto --python_out=D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service --grpc_python_out=D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service D:\xhf\project\test\my-biz\xianyu\backend\llm-manage\api\protos\admin\service\v1\llm_model.proto
```

检查退出码：

```cmd
echo %ERRORLEVEL%
```

输出 `0` 表示生成成功。

### 6.3 补齐包标记文件

protoc 只生成 pb2 文件，不会生成 `__init__.py`。以下命令必须逐行执行，不要用 `&&` 串联：

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service

if not exist admin mkdir admin
if not exist admin\service mkdir admin\service
if not exist admin\service\v1 mkdir admin\service\v1

type nul > admin\__init__.py
type nul > admin\service\__init__.py
type nul > admin\service\v1\__init__.py
```

### 6.4 检查生成结果

```cmd
dir /a admin\service\v1
```

应存在：

```text
llm_model_pb2.py
llm_model_pb2_grpc.py
__init__.py
```

验证导入：

```cmd
..\.venv\Scripts\python.exe -c "from admin.service.v1 import llm_model_pb2, llm_model_pb2_grpc; print('grpc proto import ok')"
```

期望输出：

```text
grpc proto import ok
```

### 6.5 PowerShell 脚本方式

如果允许执行 PowerShell，也可以使用：

```powershell
.\scripts\generate-llm-model-grpc.ps1
```

脚本和第 6.2 节命令等价。

## 7. 业务代码调用

业务侧不直接操作 Nacos、channel 或负载均衡，只通过 alias 获取客户端。

```python
from app.grpc import LlmModelClient


async def example(request):
    client = LlmModelClient(request.app.state.grpc_registry)
    model = await client.get(model_id=1)
    return {
        "id": model.id,
        "display_name": model.display_name,
        "model_name": model.model_name,
    }
```

当前 `LlmModelClient` 支持：

| 方法 | 对应 RPC |
|---|---|
| `get` | `Get` |
| `list_models` | `List` |
| `create` | `Create` |
| `update` | `Update` |
| `delete` | `Delete` |
| `enable` | `Enable` |

默认超时来自：

```yaml
grpc:
  client:
    default_timeout_ms: 3000
```

也可以在方法上传入 `timeout` 覆盖默认值，单位为秒。

## 8. 启动与验证

### 8.1 启动

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
..\.venv\Scripts\python.exe .\main.py
```

或：

```cmd
..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

### 8.2 HTTP 健康检查

```cmd
curl http://127.0.0.1:8000/health
```

期望返回 `status` 为 `UP`。

### 8.3 Nacos 验证

在 Nacos 控制台检查：

| 检查项 | 期望 |
|---|---|
| `goodfish-ai-service` | 已注册 HTTP 实例 |
| `goodfish-ai-service-grpc` | 不应注册，本期未启用 |
| `goodfish-llm-service-grpc` | 下游服务存在健康 gRPC 实例 |

## 9. 常见问题

### 9.1 `No module named grpc`

重新安装依赖：

```cmd
..\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

### 9.2 `No module named xxs_nacos_grpc`

安装公共模块：

```cmd
..\.venv\Scripts\python.exe -m pip install -e ..\python\xxs-nacos-grpc
```

### 9.3 `No module named admin`

说明 Python stub 未生成或不在服务根目录下。执行第 6 节流程。

### 9.4 `No available gRPC instance`

检查：

| 项 | 排查点 |
|---|---|
| namespace | 是否与 `NACOS_NAMESPACE_ID` 一致 |
| group | 是否与 `NACOS_GROUP` 一致 |
| 服务名 | 是否一字不差 |
| 端口 | 是否为 gRPC 端口 |
| 实例状态 | 是否健康且启用 |
| 网络 | 本机是否能访问下游 gRPC 端口 |

### 9.5 protoc 提示目录不存在

不要使用未展开或带尾随空格的 CMD 变量。直接使用第 6.2 节的完整绝对路径命令。

### 9.6 `type nul` 后看起来没有内容

`__init__.py` 是允许为空的包标记文件，大小为 `0` 字节是正常现象。
