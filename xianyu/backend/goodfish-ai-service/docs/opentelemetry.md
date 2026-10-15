# FastAPI OpenTelemetry 接入指南

本文说明 `goodfish-ai-service` 如何接入 OpenTelemetry。当前版本只启用 **链路追踪** 和 **日志上报**，暂不启用指标。

## 1. 方案定位

| 能力 | 状态 | 说明 |
|---|---|---|
| FastAPI HTTP Span | 已设计 | 使用 OpenTelemetry Python Contrib 的 FastAPI 插桩 |
| W3C Trace Context | 已设计 | 支持 `traceparent`、`tracestate` 和 baggage |
| gRPC Client Span | 已设计 | 下游调用自动生成客户端 Span 并透传 trace |
| 日志上报 | 已设计 | 应用日志通过 OTLP 上报并携带 trace 上下文 |
| 指标 | 暂不启用 | 不初始化 `MeterProvider`，不上报 metrics |

服务使用应用内显式初始化 SDK，而不是必须通过 `opentelemetry-instrument` 启动。这样可以继续支持 IDE 直接运行 `main.py`，并复用现有的本地配置 + Nacos 配置合并机制。

## 2. 依赖安装

依赖已写入：

```text
backend/goodfish-ai-service/requirements.txt
```

在 CMD 中执行：

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
..\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

用户自己维护虚拟环境时，只需要确保以下包已安装：

```text
opentelemetry-api
opentelemetry-sdk
opentelemetry-exporter-otlp-proto-grpc
opentelemetry-exporter-otlp-proto-http
opentelemetry-instrumentation-fastapi
opentelemetry-instrumentation-grpc
```

## 3. 配置说明

### 3.1 OTLP gRPC

本地或 Nacos 配置示例：

```yaml
opentelemetry:
  enabled: true
  service_name: goodfish-ai-service
  exporter:
    protocol: grpc
    endpoint: http://otel-collector:4317
    insecure: true
  traces:
    enabled: true
    sampling_ratio: 1.0
  logs:
    enabled: true
```

### 3.2 OTLP HTTP

```yaml
opentelemetry:
  enabled: true
  service_name: goodfish-ai-service
  exporter:
    protocol: http
    endpoint: http://otel-collector:4318
  traces:
    enabled: true
  logs:
    enabled: true
```

代码会自动为 HTTP 协议补充：

| 信号 | 路径 |
|---|---|
| Trace | `/v1/traces` |
| Log | `/v1/logs` |

也可以显式配置信号独立地址：

```yaml
opentelemetry:
  enabled: true
  exporter:
    protocol: http
    trace_endpoint: https://collector.example.com/v1/traces
    log_endpoint: https://collector.example.com/v1/logs
```

### 3.3 字段说明

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `enabled` | bool | `false` | 总开关，关闭时不初始化 Provider |
| `service_name` | str | Nacos 服务名 | Resource 中的 `service.name` |
| `exporter.protocol` | str | 环境变量或 SDK 默认 | `grpc`、`http` |
| `exporter.endpoint` | str | 环境变量或 SDK 默认 | OTLP 基础地址 |
| `exporter.trace_endpoint` | str | 空 | Trace 独立地址 |
| `exporter.log_endpoint` | str | 空 | Log 独立地址 |
| `exporter.insecure` | bool | 自动 | 仅 gRPC 使用；默认按 `https://` 前缀判断 |
| `exporter.headers` | map | 空 | OTLP 请求头，适合 Collector 鉴权 |
| `traces.enabled` | bool | `true` | 是否启用链路追踪 |
| `traces.sampling_ratio` | float | `1.0` | 采样比例，范围 `0` 到 `1` |
| `traces.schedule_delay_millis` | int | `5000` | Span 批量导出间隔 |
| `traces.export_timeout_millis` | int | `5000` | Span 导出超时 |
| `logs.enabled` | bool | `true` | 是否启用日志上报 |
| `logs.schedule_delay_millis` | int | `5000` | 日志批量导出间隔 |
| `logs.export_timeout_millis` | int | `5000` | 日志导出超时 |

`opentelemetry` 下未知字段会直接报错，避免拼写错误被忽略。

## 4. 配置来源与优先级

配置继续使用现有运行时配置机制：

| 优先级 | 来源 | 说明 |
|---|---|---|
| 高 | 显式 YAML | 本地或 Nacos 中显式写出的字段 |
| 中 | `OTEL_*` 环境变量 | 仅补齐 YAML 未配置的 endpoint、protocol、headers |
| 低 | 代码默认值 | 采样比例、批量间隔等 |

合并规则：

1. 本地 `config/application.yaml` 先加载。
2. `NACOS_DATA_ID` 非空时拉取 Nacos 配置。
3. 本地和 Nacos 配取深度并集，同名叶子以 Nacos 为准。
4. `NACOS_DATA_ID` 为空时不拉取远程配置。
5. `opentelemetry` 配置变更后不热更新，需要重启服务。

Nacos 配置监听中如果发现 `opentelemetry` 配置变化，服务会记录 WARN 日志并继续使用启动时的 Provider。

## 5. 环境变量

常用环境变量：

```text
OTEL_EXPORTER_OTLP_PROTOCOL=grpc
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4317
OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://otel-collector:4317
OTEL_EXPORTER_OTLP_HEADERS=api-key=secret
```

说明：

| 事项 | 规则 |
|---|---|
| 协议值 | `grpc`、`http`、`http/protobuf` 都可识别，`http/protobuf` 会转为 `http` |
| HTTP 协议 | 建议使用 `http://collector:4318` 基础地址 |
| Headers | 使用逗号分隔多个 `key=value` |
| 敏感信息 | 不要把 Collector 密钥提交到仓库 |

## 6. FastAPI 插桩时机

应用创建流程：

```text
1. create_app()
2. 创建 FastAPI 实例
3. 添加身份透传中间件
4. FastAPIInstrumentor.instrument_app(app)
5. lifespan 中合并本地与 Nacos 配置
6. 初始化 OpenTelemetry Provider
7. 开始处理请求
```

FastAPI 插桩必须在 Starlette middleware stack 构建前完成。OpenTelemetry 的 API 层支持先创建代理 Tracer，真实 SDK Provider 会在 lifespan 启动阶段设置。因此该方案既能使用官方 FastAPI 插桩，又能等 Nacos 配置合并完成后再决定最终观测配置。

如需排除健康检查或文档路由，可在进程启动前设置：

```text
OTEL_PYTHON_FASTAPI_EXCLUDED_URLS=/health,/docs,/redoc,/openapi.json
```

该值由 FastAPI 官方插桩读取，当前不放入 Nacos 配置，避免应用创建阶段无法读取远程配置的时序问题。

## 7. gRPC 链路

调用链路：

```text
上游 HTTP 请求
  ↓ traceparent
FastAPI Server Span
  ↓ traceparent
gRPC Client Span
  ↓ traceparent
llm-manage Server Span
```

当前 interceptor 顺序：

```text
1. OpenTelemetry grpc.aio client interceptors
2. UserContextClientInterceptor
```

职责：

| Interceptor | 职责 |
|---|---|
| OpenTelemetry aio | Span、`traceparent`、`tracestate`、baggage |
| UserContext | 用户身份、授权头、`x-client-id` |

如果下游暂未接入 OpenTelemetry，Python 侧仍会生成 FastAPI Span 和 gRPC Client Span，只是下游内部不会再继续展开同一条 Trace。

## 8. 日志上报规则

日志通过 OTLP Log Exporter 上报：

| 日志 | 是否上报 |
|---|---|
| `app.*` 且级别 >= INFO | 上报 |
| 其他 logger 且级别 >= WARNING | 上报 |
| `uvicorn.access` | 不上报 |
| DEBUG | 不上报 |

日志会自动携带当前有效 Span 的 `trace_id` 和 `span_id`。访问日志不上报，访问行为由 HTTP Span 覆盖，避免产生双份高基数数据。

## 9. 生命周期

启动顺序：

```text
1. 加载本地配置
2. 拉取并合并 Nacos 配置
3. 校验 OpenTelemetry 配置
4. 启动 TelemetryManager
5. 初始化 gRPC registry
6. 注册 HTTP 实例
7. 开始对外服务
```

关闭顺序：

```text
1. 注销 Nacos HTTP 实例
2. 关闭 gRPC registry
3. 关闭 Nacos client
4. flush 并关闭日志 Provider
5. flush 并关闭链路 Provider
```

观测 Provider 最后关闭，可以继续记录服务 shutdown 过程中的日志。flush 超时为 5 秒，避免 Collector 不可用时进程长期无法退出。

## 10. 验证

完整手动验证步骤见：

```text
backend/goodfish-ai-service/docs/opentelemetry-verification.md
```

### 10.1 启动服务

```cmd
cd /d D:\xhf\project\test\my-biz\xianyu\backend\goodfish-ai-service
..\.venv\Scripts\python.exe .\main.py
```

### 10.2 发起请求

```cmd
curl http://127.0.0.1:8000/api/llm-models
```

在 Collector 后台检查：

| 检查项 | 期望 |
|---|---|
| FastAPI Span | `GET /api/llm-models` |
| gRPC Span | 调用 `llm-manage` 时出现 |
| trace_id | FastAPI、gRPC、日志一致 |
| metrics | 不应出现该服务主动上报的业务 metrics |

### 10.3 关闭验证

停止服务时应看到：

```text
Service shutdown completed
```

且进程能自动退出，不能因 Collector 不可用长期阻塞。
