# OpenTelemetry 手动验证手册

本文用于验证 `goodfish-ai-service` 的 OpenTelemetry 接入效果。当前只验证 **链路追踪** 和 **日志上报**，不验证指标。

相关设计方案见：

```text
backend/goodfish-ai-service/docs/opentelemetry.md
```

## 1. 验证目标

完成后应确认：

| 序号 | 验证项 | 期望结果 |
|---|---|---|
| 1 | 依赖导入 | OpenTelemetry 相关包可正常导入 |
| 2 | 配置解析 | 服务能启动，无 OpenTelemetry 配置错误 |
| 3 | FastAPI Trace | Collector 收到 HTTP Server Span |
| 4 | 入口 Trace 透传 | 手动指定 `traceparent` 时，后端 Span 使用同一个 Trace ID |
| 5 | gRPC Trace | 调用 `llm-manage` 时生成 gRPC Client Span |
| 6 | 日志上报 | Collector 收到 Python 应用日志 |
| 7 | 日志关联 | 日志携带的 Trace ID / Span ID 与请求 Span 一致 |
| 8 | 指标关闭 | Collector 不应收到该服务主动上报的业务 metrics |
| 9 | 服务退出 | 停止服务时可以正常 flush 并退出 |

## 2. 安装依赖

以下命令在 CMD 中执行。

### 2.1 进入服务目录

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
```

### 2.2 安装运行依赖

```cmd
..\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

如果需要后续运行单元测试，再安装：

```cmd
..\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

### 2.3 验证依赖导入

```cmd
..\.venv\Scripts\python.exe -c "from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor; from opentelemetry.instrumentation.grpc import aio_client_interceptors; print('opentelemetry dependencies ok')"
```

期望输出：

```text
opentelemetry dependencies ok
```

## 3. 配置 OpenTelemetry

可以选择“本地配置”或“Nacos 配置”。两种配置会深度合并，同名 key 以 Nacos 为准。

### 3.1 方式一：使用本地配置

编辑：

```text
D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service\config\application.yaml
```

OTLP gRPC 示例：

```yaml
service:
  greeting: goodfish_ai

grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
  client:
    client_id: goodfish-ai-service
    default_timeout_ms: 3000

opentelemetry:
  enabled: true
  service_name: goodfish-ai-service
  exporter:
    protocol: grpc
    endpoint: http://你的Collector地址:4317
    insecure: true
  traces:
    enabled: true
    sampling_ratio: 1.0
  logs:
    enabled: true
```

OTLP HTTP 示例：

```yaml
opentelemetry:
  enabled: true
  service_name: goodfish-ai-service
  exporter:
    protocol: http
    endpoint: http://你的Collector地址:4318
  traces:
    enabled: true
  logs:
    enabled: true
```

说明：

| 协议 | 端口 | 地址示例 |
|---|---:|---|
| OTLP gRPC | `4317` | `http://collector:4317` |
| OTLP HTTP | `4318` | `http://collector:4318` |

HTTP 协议会自动追加：

```text
/v1/traces
/v1/logs
```

### 3.2 方式二：使用 Nacos 配置

如果 `.env` 中 `NACOS_DATA_ID` 非空，直接在对应 Nacos Data ID 中增加：

```yaml
opentelemetry:
  enabled: true
  service_name: goodfish-ai-service
  exporter:
    protocol: grpc
    endpoint: http://你的Collector地址:4317
    insecure: true
  traces:
    enabled: true
    sampling_ratio: 1.0
  logs:
    enabled: true
```

注意事项：

| 事项 | 说明 |
|---|---|
| Data ID | 使用你现有 `.env` 中的值，不要求固定名称 |
| namespace/group | 使用现有 Nacos 配置，不需要新建 |
| 本地配置 | 本地已有的其他 key 会保留 |
| 覆盖规则 | Nacos 同名 key 覆盖本地配置 |
| 热更新 | OpenTelemetry 配置变更不热更新，需要重启服务 |

### 3.3 使用环境变量补齐配置

`OTEL_*` 环境变量必须在服务进程启动前存在。推荐通过 IDE Run Configuration 或 CMD 设置。

CMD 示例：

```cmd
set OTEL_EXPORTER_OTLP_PROTOCOL=grpc
set OTEL_EXPORTER_OTLP_ENDPOINT=http://你的Collector地址:4317
```

如 Collector 需要鉴权：

```cmd
set OTEL_EXPORTER_OTLP_HEADERS=api-key=你的密钥
```

注意：当前 `OTEL_*` 变量读取的是进程环境变量。不要只写到 `.env` 文件中，除非你同时确认启动环境会把它注入进程环境。

## 4. 启动服务

### 4.1 IDE 启动

推荐 Run Configuration：

| 项 | 值 |
|---|---|
| Script path | `D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service\main.py` |
| Working directory | `D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service` |
| Python interpreter | `D:\xhf\project\test\my-biz\xianyu\backend\.venv\Scripts\python.exe` |

启动后重点观察：

```text
OpenTelemetry started: service=goodfish-ai-service traces=True logs=True protocol=grpc
Service startup completed
```

如果看到：

```text
OpenTelemetry configuration changed; restart the service to apply it
```

说明 Nacos 中的 OpenTelemetry 配置在运行期发生变化。重启服务后才会生效。

### 4.2 CMD 启动

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
..\.venv\Scripts\python.exe .\main.py
```

或：

```cmd
..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

## 5. 验证 HTTP 链路追踪

### 5.1 发起普通请求

```cmd
curl http://127.0.0.1:8000/api/llm-models
```

在 Collector 后台按服务名查询：

```text
goodfish-ai-service
```

期望看到类似 Span：

```text
GET /api/llm-models
```

### 5.2 手动指定 Trace ID

使用固定的 W3C Trace Context 发起请求：

```cmd
curl -H "traceparent: 00-11111111111111111111111111111111-2222222222222222-01" http://127.0.0.1:8000/api/llm-models
```

期望结果：

| 项 | 期望 |
|---|---|
| Trace ID | `11111111111111111111111111111111` |
| Parent Span ID | `2222222222222222` |
| FastAPI Span | 挂在同一个 Trace 下 |
| gRPC Span | 如果请求触发 `llm-manage`，也挂在同一个 Trace 下 |

这种方式适合在 Collector 后台快速定位本次请求。

### 5.3 验证 gRPC Span

确认下游配置存在：

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
```

然后请求：

```cmd
curl http://127.0.0.1:8000/api/llm-models
```

在 Trace 中期望看到：

```text
FastAPI Server Span
  └─ LlmModel gRPC Client Span
```

如果 `llm-manage` 也接入了 OpenTelemetry，它的 Server Span 会继续挂在同一个 Trace 下；如果下游未接入，则只能看到 Python 侧的 gRPC Client Span。

## 6. 验证日志上报

### 6.1 当前日志规则

| 日志 | 是否上报 |
|---|---|
| `app.*` 且级别 >= INFO | 上报 |
| 其他 logger 且级别 >= WARNING | 上报 |
| `uvicorn.access` | 不上报 |
| DEBUG | 不上报 |

因此验证时优先观察服务启动、业务请求、gRPC 调用和异常日志。

### 6.2 在 Collector 查询日志

按以下条件查询：

| 条件 | 值 |
|---|---|
| service.name | `goodfish-ai-service` |
| severity | INFO 及以上 |
| logger | `app.*` |

请求 `/api/llm-models` 后，确认日志的：

```text
trace_id
span_id
```

与该请求的 FastAPI Span 一致。

### 6.3 验证异常日志

如果临时制造一个下游不可用场景，例如停止 `llm-manage`，再请求：

```cmd
curl http://127.0.0.1:8000/api/llm-models
```

期望：

1. HTTP 接口返回 503 或 502。
2. Collector 中出现错误 Span。
3. Python 错误日志上报到 Collector。
4. 错误日志和错误 Span 拥有相同 Trace ID。

## 7. 验证指标未启用

在 Collector 或后端查询 metrics：

| 检查项 | 期望 |
|---|---|
| `goodfish-ai-service` 的业务 metrics | 不存在或没有新增 |
| SDK 内部 metrics | 默认也不启用 |
| traces | 正常存在 |
| logs | 正常存在 |

如果看到 metrics，先检查是否由其他 Agent、SDK 内部开关或其他进程上报，当前服务代码不初始化 `MeterProvider`。

## 8. 验证配置开关

### 8.1 关闭 OpenTelemetry

配置：

```yaml
opentelemetry:
  enabled: false
```

重启服务后：

| 项 | 期望 |
|---|---|
| 启动日志 | 不出现 `OpenTelemetry started` |
| Trace | 不上报 |
| Log | 不上报 |
| HTTP/gRPC 业务 | 正常可用 |

### 8.2 只开启日志

```yaml
opentelemetry:
  enabled: true
  traces:
    enabled: false
  logs:
    enabled: true
```

期望：

| 项 | 结果 |
|---|---|
| Trace | 不生成 FastAPI/gRPC Span |
| 日志 | 正常上报 |
| 日志 Trace ID | 可能为空，因为没有活跃 Span |

### 8.3 只开启链路

```yaml
opentelemetry:
  enabled: true
  traces:
    enabled: true
  logs:
    enabled: false
```

期望：

| 项 | 结果 |
|---|---|
| Trace | 正常上报 |
| 日志 | 不上报 |

## 9. 停止和退出验证

在 CMD 窗口按：

```text
Ctrl+C
```

期望日志顺序包含：

```text
Nacos 注销相关日志
Service shutdown completed
```

并确认：

1. Python 进程能退出。
2. Collector 不可用时也不会长期卡住。
3. 最后一批 Trace/Log 尽量完成 flush。

当前 flush 超时为 5 秒。

## 10. 常见问题

### 10.1 `ModuleNotFoundError: No module named 'opentelemetry'`

安装依赖：

```cmd
..\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

### 10.2 启动时报 `Invalid opentelemetry configuration`

常见原因：

| 原因 | 检查点 |
|---|---|
| 协议错误 | `protocol` 只能是 `grpc`、`http`、`http/protobuf` |
| 未知字段 | 检查是否写了 `metrics` 或拼写错误 |
| 数值非法 | 采样比例必须在 `0` 到 `1`，批量时间必须大于 0 |
| 类型错误 | `enabled` 必须是布尔值 |

### 10.3 没有 `OpenTelemetry started` 日志

检查：

| 项 | 说明 |
|---|---|
| enabled | 是否为 `true` |
| 配置来源 | 本地或 Nacos 是否真的加载到该配置 |
| Nacos Data ID | 是否和 `.env` 中一致 |
| 配置格式 | group 和 namespace 是否正确 |
| 启动顺序 | OpenTelemetry 在 Nacos 配置合并后启动，可能在部分启动早期日志之后出现 |

### 10.4 没有 Trace

检查：

| 项 | 说明 |
|---|---|
| traces.enabled | 是否为 `true` |
| sampling_ratio | 验证期建议设为 `1.0` |
| Collector 地址 | gRPC 使用 4317，HTTP 使用 4318 |
| 协议 | 不要用 HTTP 端口配 `grpc` |
| TLS | HTTPS Collector 不要配置 `insecure: true` |
| 网络 | 本机是否能访问 Collector 端口 |
| 后端查询时间 | 确认查询时间范围正确 |

### 10.5 没有日志

检查：

| 项 | 说明 |
|---|---|
| logs.enabled | 是否为 `true` |
| 日志级别 | `app.*` 需要 INFO 及以上 |
| access 日志 | `uvicorn.access` 已明确排除 |
| Collector 后端 | 确认 Logs 页面支持 OTLP 日志 |
| Trace 关联 | 没有活跃 Span 时，日志可能没有 Trace ID，但这不代表日志没有上报 |

### 10.6 gRPC 没有 Span

检查：

| 项 | 说明 |
|---|---|
| 请求是否触发 gRPC | `/api/llm-models` 应调用 `LlmModel` |
| 下游服务名 | `grpc.discovery.services.llm-model` 是否正确 |
| Nacos 实例 | 下游是否有健康实例 |
| 服务日志 | 是否出现 `gRPC channel updated` |
| 下游 Trace | 下游未接入时，只能看到 Python Client Span |

### 10.7 Nacos 配置修改后没有生效

OpenTelemetry 配置不热更新。日志出现以下提示时重启服务：

```text
OpenTelemetry configuration changed; restart the service to apply it
```

### 10.8 想排除健康检查 Trace

启动前设置：

```cmd
set OTEL_PYTHON_FASTAPI_EXCLUDED_URLS=/health,/docs,/redoc,/openapi.json
```

该变量由 FastAPI 官方插桩读取。
