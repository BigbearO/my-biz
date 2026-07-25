# xxs-otel-log

基于 OpenTelemetry 的 kratos 日志增强模块，将链路上下文（`trace_id` / `span_id`）以
`log.Valuer` 形式注入日志字段，使日志自动携带链路追踪信息，便于与 fluent-bit / OpenObserve
按 `trace_id` 关联日志与链路。

## 特性

- **可复用**：仅依赖 `github.com/go-kratos/kratos/v2/log` 与 `go.opentelemetry.io/otel/trace`，
  不耦合 `kratos-bootstrap`，任意 kratos 服务均可直接引入。
- **职责单一**：只"读取" context 中的 span 信息，tracer 的初始化（W3C、endpoint）由各服务自行完成。
- **安全降级**：context 中无有效 span 时返回空串，不出现无效字段值。

## 安装

```bash
go get bigbear.com/xxs-common/xxs-otel-log
```

本地联调（未打 tag 前）可在使用方 `go.mod` 加 replace：

```go
replace bigbear.com/xxs-common/xxs-otel-log => /绝对路径/xxs-common/xxs-otel-log
```

## 使用

### 业务日志（替代 `ctx.NewLoggerHelper`）

```go
import "bigbear.com/xxs-common/xxs-otel-log/logx"

// s.log = ctx.NewLoggerHelper("ai/service")
s.log = logx.NewTracedHelper(ctx.GetLogger(), "ai/service")

// 必须配合 WithContext(ctx) 使用，取值器才会读取到请求级 span
s.log.WithContext(ctx).Info("Chat receive message: ", req.GetMessage())
```

### 中间件日志（替代 `logging.Server(ctx.GetLogger())`）

```go
ms = append(ms, logging.Server(logx.TracedLogger(ctx.GetLogger())))
```

## 前提

日志中的 `trace_id` 来自 context 中的 span，因此需要服务已初始化 OpenTelemetry tracer
（例如启用 `github.com/tx7do/kratos-bootstrap/tracer`，并将 `config.yaml` 的
`trace.endpoint` 指向可用的 OTLP 端点，如 `localhost:4317`）。无 span 时日志字段为空串。
