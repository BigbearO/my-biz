# kratos-bootstrap + bear-admin 迁移到 kratos v3.0.0 方案（修订版）

> 参考实例：`kratos-admin`（依赖 `liujitcn/kratos-kit`，即 kratos-bootstrap 的 v3 移植孪生库）。
> 其源码已验证本方案的多处关键判断，下文在「参考印证」处标注。

---

## 一、背景与目标

- 目标：将 `kratos-bootstrap`（含 `logger`/`registry`/`config`/`database`/`oss`/`cache`/`rpc`/`tracer`/`transport`/`bootstrap`）、`xxs-common`、`bear-admin` 从 `go-kratos/kratos/v2` 迁移到 `/v3`。
- 核心变化（v3）：
  - 日志体系由 `log.Logger`（`Log(level, keyvals...)` 接口）改为 Go 标准库 `log/slog`（统一 `slog.Logger` + `slog.Handler`）。
  - `contrib/log/zap` 删除；`contrib/otel/tracing` 升级到 `contrib/otel/v3/tracing`。
  - `transport/http/binding` 包删除；`SupportPackageIsVersion3` 取代 `SupportPackageIsVersion1`。
  - `middleware/ratelimit` 不再接收 aegis bbr。
  - `log.Fatalf` 等包级格式化函数删除，保留包级 `log.Error(msg, args...)`。
  - `errors` 包仍是 `SupportPackageIsVersion1`（无改动）。
  - `registry` 接口（`Registrar`/`Registry`/`Instance`/`Watcher`）签名不变。

---

## 二、参考实例（`kratos-admin`）关键印证

| # | 原假设 | 参考实例印证 | 结论 |
|---|---|---|---|
| 1 | 6 个 logger 后端须改写为 `slog.Handler` | 实例 `internal/server/middleware/logstream/access_filter.go` 给出真实 `slog.Handler` 实现（`runtimeAccessFilter`，四方法齐全） | 方向正确，可直接复用此模板 |
| 2 | `middleware.Chain` / `server.Use` 签名不变 | 实例 `module.go` 仍用 `server.Use("/path", middleware.Chain(a,b,c))` | 自研中间件（`rpc/middleware/validate`、`requestid`）无需改签名 |
| 3 | 模块结构与 `kratos-bootstrap` 一致 | `kratos-kit` 同样有 `logger/zap`、`registry/consul`、`config/*`、`bootstrap`、`tracer`、`tracing` | 模块划分无需重构 |
| 4 | `kratos.New` / `*kratos.App` 仍是入口 | `wire_gen.go` 返回 `func NewApp(ctx) (*kratos.App, func(), error)` | bear-admin 的 `NewApp` 形态不变 |
| 5 | `log.Fatalf` 已删除 | 实例全部改用 `*slog.Logger` / 包级 `log.Error` | 确认须改 |
| 6 | `transport/*` 受 `tx7do/kratos-transport` 阻塞 | 实例用 `kratos-kit/transport/sse`、`transport/mcp`（liujitcn 自研，非 tx7do） | v3 兼容的 SSE/MCP/kafka 传输层真实存在，tx7do 路线需另寻解 |
| 7 | Go ≥ 1.25 | 实例 `go 1.27.0` + kratos v3.0.0 正常编译 | 升到 1.25 即可，1.27 更稳 |
| 8 | 全局 logger 安装点仍存在 | 实例 `access_filter.go` 使用 `log.Default()` / `log.SetDefault(*slog.Logger)` | bootstrap 既能 `kratos.Logger(logger)`，也能 `log.SetDefault` 兜底 |

---

## 三、Logger 模块改造（核心难点）

### 3.1 契约变更

```go
// 旧（v2）：log.Logger 接口
type FactoryFunc func(conf *conf.Logger) (log.Logger, error)
// 新（v3）：返回标准 *slog.Logger
type FactoryFunc func(conf *loggerConfig) (*slog.Logger, error)
```

### 3.2 后端实现模板（源自参考实例 `runtimeAccessFilter`）

参考实例已实现教科书级 `slog.Handler`，六后端按同一模板重写：

```go
type xxxHandler struct{ /* 底层 writer */ }

func (h *xxxHandler) Enabled(ctx context.Context, level slog.Level) bool { return true }
func (h *xxxHandler) Handle(ctx context.Context, r slog.Record) error {
    // 将 r 摊平为 map（r.Attrs 遍历），投递/落盘
    return nil
}
func (h *xxxHandler) WithAttrs(attrs []slog.Attr) slog.Handler { /* 返回带属性的副本 */ return h }
func (h *xxxHandler) WithGroup(name string) slog.Handler      { return h }
```

- `fluent` / `aliyun` / `tencent`：在 `Handle` 中把 `slog.Record` 摊平为 map 后投递，逻辑从现有 `Log()` 平移。
- `std` / `file` / `multi`：在 `Handle` 中做落盘。

### 3.3 `logger.go` 改写（用 v3 自带函数）

v3 提供：`log.NewLogger(handler slog.Handler, opts...)`、`log.NewHandler(opts...)`、`log.WithExtractor(func(ctx) []slog.Attr)`、`log.SetDefault`、`log.Default`、`log.WithAddSource`、`log.WithFilter`。

```go
func NewLogger(cfg *conf.Logger) (*slog.Logger, error) {
    h, err := buildHandler(cfg) // 根据 cfg.Name 选择 6 后端之一
    if err != nil { return nil, err }
    // trace 字段提取：优先用 go.opentelemetry.io/otel/trace 自写 Extractor（零 contrib 依赖）
    return log.NewLogger(h, log.WithExtractor(traceAttrsExtractor)), nil
}

func initDefault() {
    if l, err := NewLogger(globalConfig); err == nil { log.SetDefault(l) }
}
```

> trace 字段提取二选一：① 自写 `otel/trace` Extractor（保持 `logger` 模块零 contrib 依赖）；② 直接依赖 `contrib/otel/v3/tracing` 的 `TraceAttrs(ctx)`。

### 3.4 `logger/zap` 后端改用 `zapslog`

`contrib/log/zap` 在 v3 已删除。最稳妥做法是用官方维护的 zap→slog 适配：

```go
import "go.uber.org/zap/exp/zapslog"

func NewZapHandler(logger *zap.Logger) slog.Handler {
    return zapslog.NewHandler(logger.Core(), zapslog.WithLevel(logger.Level()))
}
// 注册：_ = logger.Register(logger.Zap, func(cfg *conf.Logger) (*slog.Logger, error) {
//     return log.NewLogger(NewZapHandler(zl)), nil
// })
```

---

## 四、其他模块改造要点

### 4.1 `registry` / `config` / `database` / `oss` / `cache`
- 仅替换 import 路径 `v2 → v3`。
- 函数签名中 `log.Logger` 改为 `*slog.Logger`。
- 所有 `log.Fatalf(...)` / `log.Fatalln(...)` 改为 `log.Error(...)` + `os.Exit(1)`（或返回 error 由调用方处理）。

### 4.2 `tracer`
- v3 仅依赖 `go.opentelemetry.io/otel` 系列，`tracer` 模块本身几乎不变（仅改 import）。
- 若内部用到 `contrib/log` 的 zap 适配，按 3.4 改。

### 4.3 `rpc`
- `middleware/tracing`：从 `contrib/otel/tracing` 迁移到 `github.com/go-kratos/kratos/contrib/otel/v3/tracing`。
- `TraceID`/`SpanID` 改为传入 `context.Context`，新增 `TraceAttrs(ctx) []slog.Attr` 供日志提取。
- `middleware/ratelimit`：删除 aegis 依赖，`WithLimiter` 不再接收 aegis bbr，改用 `ratelimit.Server()`（默认 bbr）。
- `log.Fatalf → log.Error`。

### 4.4 `bootstrap`
- `Context.logger` 字段由 `log.Logger` 改为 `*slog.Logger`。
- 入口形态参考实例 `bootstrap.NewContext(ctx, &configv1.AppInfo{...})` + `bootstrap.RunApp(ctx, NewApp)`（保留自有 API 名称）。
- 可用 `kratos.Logger(logger)` 或 `log.SetDefault(logger)` 安装全局 logger。

### 4.5 `transport/*`（kafka / sse / mqtt / redis / mcp）
- 受 `tx7do/kratos-transport` 是否支持 v3 决定，见阶段 0 与阶段 9。

### 4.6 `xxs-common`
- `logx` 按 §3 同模板重写为 `slog.Handler` / `WithExtractor`，统一 `*slog.Logger` 门面。

---

## 五、bear-admin 改造要点

1. `Makefile` 中 protoc 插件 `protoc-gen-go-kratos` / `protoc-gen-go-http` / `protoc-gen-go-errors` 全部升 `/v3`。
2. 删除 `.proto` 后的 `buf generate`，重新生成 `*.pb.go`（含 `*_errors.pb.go`，errors 包仍是 v1 无需改；`*_http.pb.go` 必须重生成以满足 `SupportPackageIsVersion3`）。
3. 业务代码 `log.Helper` → `*slog.Logger`（`log.WithContext(ctx)` 仍需保留以带入请求级字段）。
4. `main.go` 显式 `_ import "github.com/go-kratos/kratos/v3/encoding/protojson"`（v3 不再自动注册）。
5. 重新执行 `go generate ./...` / `wire` 生成 `wire_gen.go`。

---

## 六、分阶段计划

| 阶段 | 内容 | 工时 |
|---|---|---|
| 0 | 准备：切分支、`go` 升 1.25、补本地 `replace`、`go list -m -json github.com/tx7do/kratos-transport@latest` 验证 v3 支持 | 0.5d |
| 1 | `api`（仅 protobuf，升 Go）+ `tracer`（仅 otel） | 0.5d |
| 2 | `logger` 模块族：`FactoryFunc → (*slog.Logger, error)`；`logger.go` 用 `log.NewLogger`+`WithExtractor`；6 后端按 §3.2 模板重写；`zap` 走 `zapslog` | 3.5d |
| 3 | `registry`/`config`/`database`/`oss`/`cache`：路径 `v2→v3` + `log.Logger→*slog.Logger` + `log.Fatal*→log.Error` | 2.5d |
| 4 | `rpc`：`tracing`→`contrib/otel/v3/tracing`、`ratelimit` 去 aegis、`log.Fatal*→log.Error` | 1.5d |
| 5 | `bootstrap`：`Context.logger` 改 `*slog.Logger`；参考实例入口形态 | 1.0d |
| 6 | `xxs-common`：`logx` 按 §3 模板重写 | 1.5d |
| 7 | `bear-admin`：`Makefile` 插件升 `/v3`、重跑 `buf generate`、业务 `log.Helper→*slog.Logger`、`main.go` 显式注册 `encoding/protojson`、重跑 wire | 2.0d |
| 8 | 全量验证（`go build ./...`、`go test ./...`、本地起服看日志/slog 链路） | 1.0d |
| 9 | `transport/*`：按阶段 0 结论走 A/B/C | 1.0d 或 0 |

**合计约 14~15 人日**（阶段 9 为 0 时约 13.5d）。

### 阶段 0 的 transport 验证命令（决定阶段 9 走向）

```powershell
go list -m -json github.com/tx7do/kratos-transport@latest
# 查看返回 JSON 中的 go.mod 依赖里 go-kratos/kratos 是否为 v3
```

- 若为 v3：阶段 9 走 **方案 A**，直接升级 `tx7do/kratos-transport`。
- 若仍为 v2：走 **方案 B**（fork/移植 `kratos-kit/transport/{sse,mcp,kafka}` 的 v3 实现到 `kratos-bootstrap/transport/*`）或 **方案 C**（本次不迁这 5 个模块，保持 v2 分支独立发版）。

---

## 七、待拍板事项

1. 阶段 0 的 transport 验证：是否现在就用 `go list` 探测 `tx7do/kratos-transport` v3 支持？还是直接走方案 C（本次不迁 `transport/*`）？
2. `logger` 的 trace 字段：自写 `otel/trace` Extractor（零 contrib 依赖）还是直接依赖 `contrib/otel/v3/tracing` 的 `TraceAttrs`？
3. `xxs-common` 是否本次一并迁移（独立仓库，需单独提交 + 本地 `replace` 验证）？

---

## 八、风险与注意

- `transport/http/binding` 删除 + `SupportPackageIsVersion3` 升级：bear-admin 的 `*_http.pb.go` **必须**重新生成，否则编译失败。
- `encoding/json` 需显式 `_ import`，否则 protojson 响应异常。
- `log.Fatalf` 全仓替换量较大，建议脚本批量扫描 `search_content` 定位后逐一确认语义（exit 还是返回 error）。
- `errors` 包 `SupportPackageIsVersion1` 不变，`*_errors.pb.go` 无需重生成（但重新 generate 也无害）。
