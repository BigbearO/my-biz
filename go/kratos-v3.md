# go-wind 迁移 kratos v3 改进方案（kratos-bootstrap + gow）

> **本文档定位**：不是又一份「v2→v3 差异清单」，而是一份**可执行的工程改进方案**。
> 它同时解决三件事：
> 1. **纠偏**——`v2ToV3.md`、`wind迁移v3.md` 中若干与 kratos v3.0.0 实际发布不符的判断（已实测）；
> 2. **迁移**——`kratos-bootstrap` 从 kratos v2（v2.9.2）迁到 kratos v3（v3.0.0）；
> 3. **治本 + v3 化**——`gow`（`go-wind-toolkit/gowind`）**直接迁移到 v3、不做 v2 兼容**（§5.0），
>    同时一次性修掉生成项目「生成完不能编译」（`skill.md` 的 10 个问题），
>    并避免重蹈 `kratos-admin` 的「重、耦合、不可自定义」。
>
> **证据基线**（本次实测，非推断）：
> - `github.com/go-kratos/kratos/v3@v3.0.0`（源码已落 `GOMODCACHE`，逐文件核对）
> - `github.com/go-kratos/kratos/contrib/otel/v3@v3.0.0-20260626125723-668db92c2c00`
> - 各第三方依赖 `.mod` 实际内容（见 §2 依赖阻塞矩阵）
> - `kratos-admin/backend`（`kratos v3 v3.0.0` + `liujitcn/kratos-kit v0.0.85`）作为 v3 参考实现
> - `bear-admin`（当前 `kratos v2 v2.9.2`）、`kratos-bootstrap`（v2）、`gowind`（v2 模板）
>
> **2026-09 实拉取基线更新**（四个仓已拉到本地并逐文件核对，取代上表中过时的「现状」描述）：
>
> | 仓 | kratos 版本 | Go 版本 | 关键变化 |
> |---|---|---|---|
> | `kratos-bootstrap` | **仍 v2.9.2**（56 个 `go.mod`，逐一核对） | 多数 **1.25.0～1.26.4**；根 `1.23.0`+`toolchain 1.24.3`；`api`/`ai/model`/`script_engine*` 仍 `1.24.6` | **`logger` 已重构为项目自有 ctx-first `Logger` 接口 + slog 实现 + 双向适配器**（§4.2.0）；Go 版本已大面积升级 |
> | `kratos-transport` | **仍 v2.9.2**（62 个 `go.mod`） | — | 硬阻塞**未解除**，§4.6 方案 C 结论不变 |
> | `go-wind-admin-template` | **仍 v2.9.2**，`go 1.25.4` | 1.25.4 | 结构：`api/ app/ pkg/ script/ app.mk Makefile` |
> | `go-wind-toolkit/gowind` | **不依赖 kratos**（仅模板字符串出现） | `go 1.25.5` | 规模翻倍（165 文件）；新增 `build/migrate/extract` 命令、`pkg/frontendgen`、**手写装配 `wiring.tpl`**；`ent add` 已修复可用 |
>
> ⚠️ 文档中凡与上表冲突的「现状」描述，**以上表为准**；受影响章节已就地标注修订（§2 / §4.1 / §4.2 / §4.7 / §5 / §6 / §7）。

---

## 0. 结论先行（TL;DR）

| 问题 | 结论 |
|---|---|
| kratos v3 好不好升级？ | 核心 API 破坏面**比文档描述的小**：`registry` / `config` / `middleware` / `transport.Server` / `kratos.New` 签名全都没变；**真正变的只有日志（→ `log/slog`）、HTTP binding 包位置、`middleware/tracing` 与 `middleware/metrics` 移出核心**。 |
| 最大难点是什么？ | ~~logger 模块族（6 个后端重写为 `slog.Handler`，约占 40% 工作量）~~ → **已作废**（2026-09 实测）：`logger` 早已重构为**项目自有 ctx-first `Logger` 接口 + slog 实现**，kratos 耦合**仅剩 `logger.go` + `adapter.go` 两个文件**，v3 只需换一个 `slog` 桥，**≤0.5d**（详见 §4.2.0）。现在的难点回到 **`gow` 生成物治本**（§5）。 |
| 有没有硬阻塞？ | 有 2 个（**2026-09 实测确认仍未解除**）：`tx7do/kratos-transport/transport/*`（62 个 `go.mod` 仍 `v2.9.2`）与 `tx7do/kratos-authn`。`go-crud` 实测当前 `v0.0.53`，需升到 **`>= v0.0.55`** 才解除 kratos 依赖。 |
| 推荐策略？ | `kratos-bootstrap` **新增 `/v3` 模块谱系**（`.../bootstrap/v3`、`.../logger/v3` …），与 v2 谱系**并存**。 |
| **`gow` 要不要兼容 v2？** | **不兼容，直接 v3 单一输出**（已定，见 §5.0）：不引入 `--kratos-version` 开关，模板与依赖**硬编码 v3**；遇到 `kafka/sse/mqtt/mcp/asynq` **直接报错退出**，不做静默降级。 |
| `transport/*` 怎么办？ | **已定：采用方案 C，本次不迁移**（v3 谱系不发布 `transport/*`）。依据：`bear-admin` 全仓 **0 处**使用 `kratos-transport`/`kratos-bootstrap/transport`，冻结**不产生功能回退**；`gow` 默认链路也不触碰它。详见 §4.6。 |
| 为什么不用 kratos-admin 那套？ | `kratos-admin` 把「装配」全部塞进私有 `kratos-core`/`kratos-kit`（40+ 子模块、`wire.Struct(new(Services), "*")` 巨型聚合、包级单例、直接改 `server.Server.Handler`），导致**不可替换**。本方案反其道：**薄装配 + 显式注入 + 可 replace**。 |
| **不发布，生成的项目怎么拿本地代码？** | **不需要发布**：用 `go.work`（个人联调，零 `go.mod` 污染）+ `go.mod` 目录 `replace`（提交/CI），Go 直接读磁盘目录、零网络。前提是先把本地 `go.mod` 的 `module` 行改成 `/v3` 终态路径。完整答案与命令见 **§4.8**，`gow` 侧的自动化见 **§5.5**。 |

**总工作量估算**：约 **11.5 人日**（详见 §7；较原估 15d 下调 3.5d，主因是 logger 从 3.5d 降到 0.5d，见 §4.2.0；`transport/*` 已定为方案 C，其 fork 工时（≈1d）**不计入**）。

---

## 1. 事实校正：既有文档中与实际不符的 6 处

> 这些点如果不纠正，会写出**编译不过**或**多引入依赖**的代码。

| # | 既有文档的说法 | 实测事实 | 影响 |
|---|---|---|---|
| 1 | 「`contrib/encoding/json/v3` 保留 v2 兼容 JSON codec」「v3 需在 `main.go` 显式 `_ import "…/v3/encoding/protojson"`」 | ❌ `contrib/encoding/json/v3` **不存在**（v2/v3 都无此模块）。✅ 而 `kratos/v3/transport/transport.go` **已内置** `_ import` 全部 codec（`encoding/form`、`json`、`proto`、`protojson`、`xml`、`yaml`）。 | **`main.go` 无需任何 codec 注册**。加了反而可能是无用 import。 |
| 2 | 「`middleware/tracing` → `contrib/otel/tracing` 升级到 `contrib/otel/v3/tracing`」 | ✅ 方向对。精确事实：v3 核心 `middleware/` **只剩** `circuitbreaker / logging / metadata / ratelimit / recovery / selector / validate`（`tracing`、`metrics`、`auth` 均被移出）。`contrib/otel/v3/tracing` 提供 `Server()`、`Client()`、`TraceID(ctx)`、`SpanID(ctx)`、`TraceAttrs(ctx) []slog.Attr`。 | `rpc` 模块与所有 `tracing.Server()` 调用点必须改路径；**`TraceAttrs` 正好可直接喂给 `log.WithExtractor`**。 |
| 3 | 「`contrib/log/zap` 删除」✅ 但未给替代 | ✅ v3 无 `contrib/log/zap/v3`（仅 `contrib/log/zap/v2@v2.0.0-20260404020628`）。替代 = `go.uber.org/zap/exp/zapslog`。**注意 zap v1.27+ 已把 `exp` 拆成独立模块**：需另 `go get go.uber.org/zap/exp@v0.3.0`，真实签名为 `zapslog.NewHandler(core zapcore.Core, opts ...HandlerOption) *Handler`，选项为 `WithCaller/WithCallerSkip/WithName/AddStacktraceAt(slog.Level)`。 | `logger/zap` 后端必须重写为 `zapslog.NewHandler(core)`，并新增一条 module 依赖。 |
| 4 | 「默认熔断器不再依赖 `aegis`，需 `circuitbreaker.WithBreakerFactory`」 | ✅ 对。但**同样适用于 ratelimit**：v3 `middleware/ratelimit.Server()` 内部已自建 limiter（`internal/ratelimit`，自带 bbr 实现），`Limiter`/`DoneFunc` 变成 `internal` 包的类型别名 → **`github.com/go-kratos/aegis` 可整个删除**，`WithLimiter` 只在需要自定义限流器时使用。 | `kratos-bootstrap/rpc/go.mod` 可去掉 `aegis`；`rpc/grpc.go`、`rpc/rest.go` 里那段 `aegis/bbr` 新建 limiter 的代码可删除。 |
| 5 | 「`transport/http/binding` 包已移除，`binding.EncodeURL` → `http.BuildPath`」 | ✅ 精确：`transport/http/binding/` 子包**确实没有**了；功能并入 `transport/http` 包：`http.BuildPath`（含 `WithQueryParams`/`WithOmitFields`）、`http.Context` 上的 `Bind/Vars/Form/Query/BindVars/BindQuery/BindForm`。 | 手写代码按此改；`*_http.pb.go` 重生成即可。 |
| 6 | 「`log.Fatalf` 等包级格式化函数删除，保留包级 `log.Error(msg, args...)`」 | ✅ 但对齐到 slog 后语义变了：v3 `log.Error(msg string, args ...any)` ↔ `slog.Error`（**key-value 对**，不是 `printf`）。`log.Errorf/Warnf/Infof` **全部不存在**。`log.LevelFatal = slog.LevelError + 4` 存在，但**没有** `log.Fatal` 包级函数。`log.NewStdLogger` / `log.NewHelper` / `log.With(logger, kv...)` / `log.DefaultTimestamp` / `log.DefaultCaller` **全部删除**。 | 全仓 `log.Fatalf("[ENT] failed: %v", err)` → 必须改成 `log.Error("ent 初始化失败", "err", err)` + `os.Exit(1)`（或返回 error）。`log.With(c.logger, "module", name)` → `c.logger.With("module", name)`。 |

**另外一条好消息**：`github.com/tx7do/kratos-swagger-ui`（含 bear-admin 用的 `v0.0.0-20250528131001` 版）**完全不依赖 kratos**——它用泛型约束 `interface{ HandlePrefix/Handle/HandleFunc }` 接收 server。v3 `*http.Server` 这三个方法都在（`HandlePrefix`/`Handle`/`HandleFunc`），所以 **swagger-ui 零改动可用**。

---

## 2. 依赖阻塞矩阵（实测 `.mod` 内容）

| 依赖 | 最新版 | 是否依赖 kratos | 对 v3 的影响 | 处置 |
|---|---|---|---|---|
| `github.com/go-kratos/kratos/v3` | **v3.0.0**（`go 1.25.0`） | — | 目标 | 直接升 |
| `kratos/contrib/otel/v3` | `v3.0.0-20260626125723-…` | v3 ✅ | 可用 | 用于 `tracing`/`log`/`metrics` |
| `kratos/contrib/middleware/jwt/v3` | `v3.0.0-20260626125723-…` | v3 ✅ | 可用 | JWT 从此处引入 |
| `kratos/contrib/registry/nacos/v3` | `v3.0.0-20260626125723-…` | v3 ✅ | 可用 | — |
| `kratos/contrib/config/nacos/v3` | `v3.0.0-20260626125723-…` | v3 ✅ | 可用 | — |
| `kratos/contrib/middleware/validate/v3` | `v3.0.0-20260626125723-…` | v3 ✅ | 可用 | — |
| `tx7do/kratos-transport/transport/*` | 实测 `transport v1.3.4` / `broker v1.3.9`（2026-09） | **v2.9.2** ❌（**未变**，62 个 `go.mod` 逐一核对） | **硬阻塞仍未解除** | **已定（方案 C）**：v3 谱系**不发布** `transport/*`；`bear-admin` 亦未使用，无功能回退。见 §4.6 |
| `tx7do/kratos-authn` | `v1.1.11` | **v2.9.2** ❌ | **硬阻塞** | 不作为 v3 默认依赖；`gow` 模板**去掉 authn/authz 默认耦合**（正好对应 `skill.md` #2） |
| `tx7do/kratos-authz` | `v1.1.8` | 无 kratos 依赖 ✅ | 可用 | 保留为可选 |
| `tx7do/go-crud/entgo` | 实测 **`v0.0.53`**（2026-09；原文记为 `v0.0.51`） | `0.0.51` 依赖 v2.9.2 ❌；**`>= v0.0.55` 已无 kratos 依赖** ✅ | 升版本即可解锁 | 模板 pin **`>= v0.0.55`**（实测的 `0.0.53` **仍不够**，须继续升） |
| `tx7do/kratos-swagger-ui` | 任意近版 | 无 kratos 依赖 ✅ | 可用 | 零改动 |
| `tx7do/kratos-bootstrap`（本仓） | 子模块 v0.1.x | v2.9.2 ❌ | 本次迁移对象 | 见 §4 |
| `tx7do/go-utils` / `id` / `mapper` | v1.1.40 等 | 无 kratos ✅ | 可用 | 直接升 |
| `tx7do/go-wind` | 实测 **v0.0.2**（2026-09 新出现，为 `database/ent` 的 indirect 依赖） | 无 kratos ✅ | 可用 | 新增项，原文未收录；需确认其与 `go-crud` 的职责边界 |
| `tx7do/go-wind-plugins/encoding{,/json}` | 实测 **v0.0.1**（2026-09 新出现，`database/ent` 的 indirect 依赖） | 无 kratos ✅ | 可用 | 新增项；v3 的 `encoding/json` codec 策略需与它对齐（§1 #1） |

---

## 3. 设计原则（从 `kratos-admin` 的教训反推）

`kratos-admin` 能跑，但「太重、耦合严重、无法自定义」。根因已定位（详见其 `go.mod` 与 `internal/module/module.go`）：

| kratos-admin 的做法（反例） | 本方案的原则 |
|---|---|
| 40+ 个私有子模块（`liujitcn/kratos-kit/*`），版本各自漂移，无 `go.work`/`replace`，改一处必须发版 | **每个组件一个独立 module + 用户可 `replace` 到本地**；`gow` 生成的项目**主动写出 `replace` 注释块**，把「改」的门槛降到 0 |
| `init()` 匿名导入 + 全局注册表（`logger.Register`、driver、registry），**忘记导入就 nil** | **显式装配优先**：默认实现在 `NewLogger` 内部兜底（std），`init` 注册只作扩展。**绝不允许「忘记匿名导入 → 运行时 nil 日志」** |
| 包级单例：`var defaultHub = newHub()`、`var defaultPublisher atomic.Pointer[...]`、`sdk.Runtime.GetKey()` | 依赖一律走 `*bootstrap.Context` / 构造函数参数；包级变量只允许做「默认值」，且必须可被显式覆盖 |
| `wire.Struct(new(Services), "*")` 把 40+ Service 塞进一个巨型结构，`RegisterHTTP/GRPC/MCP` 各 40+ 行枚举 | 每个 service 自带 `ProviderSet`；注册用**循环 + 接口切片**，不枚举具体类型 |
| `server.Server.Handler = securityheaders.NewHandler(...)` 直接改框架内部字段 | 只用公开 API：`srv.Use(path, mw...)`、`http.Filter(...)`、`kratos.Server(...)` |
| 业务配置契约由私有 `*configv1.Bootstrap` 决定，无法本地扩展 | `kratos-bootstrap/api` 的 `conf.Bootstrap` 保持**纯 protobuf + `RegisterCustomConfig` 扩展点**，业务自定义配置走 `Context.RegisterCustomConfig` |
| 入口/生命周期被 `kratos-core` 垄断，只能挂 `module.Module` | `bootstrap.RunApp(ctx, initApp)` 保留；`initApp` 由用户自己的 wire 决定 server 组合 |

**一句话**：`kratos-bootstrap` 只做**装配**，不做业务；**每一层都必须能在不 fork 的前提下被替换**。

---

## 4. `kratos-bootstrap` 改造方案

### 4.1 版本与模块策略（关键决策）

**推荐：新增 `/v3` 模块谱系，与 v2 并存。**

理由：
- Go 的 `/vN` 后缀要求模块 **major ≥ 2**。新建路径 `github.com/tx7do/kratos-bootstrap/logger/v3` 并**打 `v3.0.0` tag**（`logger/v3/v3.0.0`）即可，语义正确；
- v2 谱系（`.../logger@v0.1.x`）**原样保留**，`bear-admin`、其他存量项目不受影响；
- **`transport/*` 因硬阻塞留在 v2 谱系**，天然形成「部分迁移」，这是单谱系方案做不到的；
- 一个进程里不会同时出现 v2/v3 两份 kratos（`kratos/v2` 与 `kratos/v3` 是完全不同的 module 路径，可共存但不互通），迁移期边界清晰。

```
github.com/tx7do/kratos-bootstrap/logger/v3        v3.0.0
github.com/tx7do/kratos-bootstrap/bootstrap/v3     v3.0.0
github.com/tx7do/kratos-bootstrap/rpc/v3           v3.0.0
github.com/tx7do/kratos-bootstrap/config/v3        v3.0.0
github.com/tx7do/kratos-bootstrap/registry/v3      v3.0.0
github.com/tx7do/kratos-bootstrap/cache/redis/v3   v3.0.0
github.com/tx7do/kratos-bootstrap/database/*/v3    v3.0.0
github.com/tx7do/kratos-bootstrap/oss/*/v3         v3.0.0
github.com/tx7do/kratos-bootstrap/tracer/v3        v3.0.0   （仅改 go 版本）
github.com/tx7do/kratos-bootstrap/api/v3           v3.0.0   （纯 protobuf，仅改 go 版本）
github.com/tx7do/kratos-bootstrap/config/*/v3      v3.0.0
github.com/tx7do/kratos-bootstrap/registry/*/v3    v3.0.0
× github.com/tx7do/kratos-bootstrap/transport/*                不发 v3（见 §4.6）
```

**目标 Go 版本**：所有模块 **`≥ go 1.25.0`**（kratos v3 自身要求）。

现状（2026-09 实拉取，**56 个 `go.mod` 全量核对**）：只有 **11 个模块低于 1.25.0**，其余 45 个**已达标**（1.25.0 ～ 1.26.4）：

| 区间 | 数量 | 模块 |
|---|---|---|
| **`< 1.25.0`（需升级）** | **11** | 根 `1.23.0`（+`toolchain 1.24.3`）、`api`、`ai/model`、`script_engine`、`script_engine/source/{s3,redis,http,git,etcd,database,consul}` —— 除根外均为 `1.24.6` |
| `1.25.0 ～ 1.25.8` | 多数 | `logger/*`（1.25.0；`aliyun` 1.25.8）、`config/*`、`registry/{nacos,polaris,zookeeper,servicecomb,eureka}`、`cache/redis`、`oss/*`、`tracer`、`database/cassandra`、`bootstrap`(1.25.3)、`transport/{sse,kafka,asynq,mqtt}`(1.25.3)、`transport/mcp`(1.25.5) |
| `1.26.x` | 较多 | `rpc`(1.26.0)、`database/{ent 1.26.4, gorm/clickhouse/doris/elasticsearch/opensearch/mongodb/influxdb 1.26.3}`、`config/{etcd,consul,kubernetes}`、`registry/{etcd,consul,kubernetes}`、`ai/{eino,langchaingo}` |

> **原方案「统一切到 1.25.0」不再需要全仓反向下压**——已高于 1.25.0 的模块**保持原样**，只需把上述 **11 个 `< 1.25.0` 的模块**抬到 `≥ 1.25.0`。原文担心的「`toolchain` 冲突难以复现」风险已大幅降低。

**替代方案（不推荐）**：单谱系在 `v0.x` 上直接破坏式升级（v0 允许 break）。改动量小，但会让所有存量 v2 用户在某次 `go get -u` 后突然编译失败，且 `transport/*` 无法优雅留后。

> `/v3` 后缀在「本地改、暂不发布」的阶段**依然有实际收益**（fail-fast，避免漏 `replace` 时静默拉回 v2），
> 且发布时不需要二次改名。详见 **§4.8.1**。

---

### 4.2 `logger` 模块族改造（**工作量已大幅缩减**）

#### 4.2.0 现状与结论修订（2026-09 实拉取，权威）

> ⚠️ **本节推翻原方案（4.2.1–4.2.3）对 logger 的判断。** 原方案假设「`FactoryFunc` 需改为返回 `*slog.Logger`，6 个后端全部重写为 `slog.Handler`，占整体工作量约 40%」。实测该改造**已在 2026-09 版本中基本完成**。

**实测现状**（`kratos-bootstrap/logger`，kratos 仍是 v2.9.2）：

| 文件 | 内容 | 依赖 kratos？ |
|---|---|---|
| `interface.go` | 项目自有 **ctx-first `Logger` 接口**（`Debug/Info/Warn/Error(ctx, msg, keyvals...)` + `With`）、`stdLogger`（基于 `log/slog`）、`traceLogger`（从 ctx 提取 `trace_id`/`span_id`）、`Helper`（`Debugf/Infof/...`）、全局 `SetLogger/GetLogger` | ❌ 零依赖 |
| `factory.go` | `FactoryFunc func(cfg *conf.Logger) (Logger, error)`——返回**项目 `Logger`**，非 kratos `log.Logger` | ❌ |
| `types.go` | `Type` 枚举（`std/fluent/logrus/zap/aliyun/tencent/zerolog`） | ❌ |
| `zap/` `logrus/` `zerolog/` `fluent/` `aliyun/` `tencent/` | **已直接实现 `logger.Logger`**（如 `logger/zap/zap.go:13`：`var _ bLogger.Logger = (*Logger)(nil)`） | ❌ |
| `adapter.go` | `AsKratosLogger`（项目→kratos）/ `FromKratosLogger`（kratos→项目），用到 `log.Valuer`、`log.DefaultMessageKey`、`log.With` | ✅ 耦合点之一 |
| `logger.go` | `NewLoggerProvider` 用 `log.With` + `log.DefaultTimestamp`/`DefaultCaller` 挂字段；`NewKratosLogger`/`NewKratosStdLogger` 兼容入口 | ✅ 耦合点之二 |

> 实测 `grep go-kratos/kratos logger/**/*.go` **仅命中 2 个文件**：`logger/logger.go:9`、`logger/adapter.go:6`。

**修订后的 v3 目标（≤0.5d，原估 3.5d）**：

1. **契约保持不变**：`FactoryFunc` 继续返回 `(Logger, error)`（项目接口），**不要**改成 `(*slog.Logger, error)`。
2. **6 个后端零改动**：已实现项目 `Logger`，v3 下只需随父模块路径加 `/v3`。
3. **只改 2 处桥接**：
   - `adapter.go` → 换成**一个** `slog.Handler` 桥（项目 `Logger` → `*slog.Logger`）。理由：kratos v3 的 `kratos.Logger(...)`、`logging.Server(...)` 只接受 `*slog.Logger`；
   - `logger.go` → 去掉 `log.DefaultTimestamp`/`log.DefaultCaller`（slog 自带 `time`/`source`），`NewLoggerProvider` 直接返回项目 `Logger`。
4. **`log.Valuer` 在 v3 已删除**，但项目侧本就不用它——trace 字段由 `WrapTrace`（读 ctx）负责，该机制在 v3 下**完全不变**。

**结论**：logger 从「全案最大难点」降级为「两个文件的桥接替换」。

#### 4.2.1 契约变更（修正）

```go
// ❌ 原方案的错误假设（已作废）：
//   type FactoryFunc func(cfg *conf.Logger) (*slog.Logger, error)

// ✅ 实际（2026-09，保持不变）：
type FactoryFunc func(cfg *conf.Logger) (Logger, error)   // Logger = 项目自有 ctx-first 接口
```

> 项目 `Logger` 接口即稳定的抽象层；**不再需要**「整个模块只依赖 `log/slog`」这类改造——`slog` 已在 `interface.go` 内作为 `stdLogger` 的实现存在。

#### 4.2.2【已过时·仅存档】原设想：`logger/logger.go` 重写

> ⛔ 以下代码基于**修订前假设**（`NewLogger` 返回 `*slog.Logger`、依赖 `kratoslog.NewLogger/WithExtractor/WithFilter`），**与实测现状不符，请勿据此施工**。正确口径见 §4.2.0。

```go
package logger

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"sort"
	"strings"

	"go.opentelemetry.io/otel/trace"

	kratoslog "github.com/go-kratos/kratos/v3/log"

	conf "github.com/tx7do/kratos-bootstrap/api/v3/gen/go/conf/v1"
)

// traceAttrs 零 contrib 依赖的 trace 字段提取器。
// 若已引入 contrib/otel/v3/tracing，可直接用 tracing.TraceAttrs 替代。
func traceAttrs(ctx context.Context) []slog.Attr {
	sc := trace.SpanContextFromContext(ctx)
	if !sc.IsValid() {
		return nil
	}
	return []slog.Attr{
		slog.String("trace_id", sc.TraceID().String()),
		slog.String("span_id", sc.SpanID().String()),
	}
}

// NewLogger 按 cfg.Type 创建 slog.Logger。
// 关键改进：即使没有任何后端 init() 注册，也能返回可用的 std logger（杜绝 nil）。
func NewLogger(cfg *conf.Logger) (*slog.Logger, error) {
	if cfg == nil || cfg.GetType() == "" || strings.EqualFold(cfg.GetType(), string(Std)) {
		return NewStdLogger(), nil
	}

	typ := Type(strings.ToLower(cfg.GetType()))
	f, ok := GetFactory(typ)
	if !ok {
		available := ListFactories()
		strs := make([]string, 0, len(available))
		for _, t := range available {
			strs = append(strs, string(t))
		}
		sort.Strings(strs)
		return nil, fmt.Errorf("unsupported logger type: %s; available: %v", typ, strs)
	}
	lg, err := f(cfg)
	if err != nil {
		return nil, fmt.Errorf("create logger %s: %w", typ, err)
	}
	if lg == nil {
		return nil, fmt.Errorf("logger factory %s returned nil logger", typ)
	}
	return lg, nil
}

// NewStdLogger 控制台 JSON logger（kratos v3 的 log.NewLogger 已带 context attrs 合并 + 过滤装饰）。
func NewStdLogger() *slog.Logger {
	return kratoslog.NewLogger(
		slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}),
		kratoslog.WithExtractor(traceAttrs),
		kratoslog.WithFilter(kratoslog.FilterKey("password", "token", "secret")),
	)
}

// NewLoggerProvider 创建带标准字段的 logger；任何异常都回退到 std，绝不返回 nil。
func NewLoggerProvider(cfg *conf.Logger, appInfo *conf.AppInfo) *slog.Logger {
	l, err := NewLogger(cfg)
	if err != nil || l == nil {
		l = NewStdLogger()
	}

	// 全局兜底：让包级 log.Info/log.Error 可用
	kratoslog.SetDefault(l)

	if appInfo != nil {
		l = l.With(
			slog.String("service.id", appInfo.GetAppId()),
			slog.String("service.instance", appInfo.GetInstanceId()),
			slog.String("service.version", appInfo.GetVersion()),
			slog.String("service.project", appInfo.GetProject()),
		)
	}
	return l
}
```

> 注意：`ts`/`caller` 不再需要手工注入——`slog.NewJSONHandler` 自带 `time`/`source`；如需再放宽可加 `log.WithAddSource(true)`。

#### 4.2.3【已过时·仅存档】原设想：六个后端统一 `slog.Handler` 模板

> ⛔ **该改造已不必要**。实测：6 个后端已直接实现项目 `Logger`（§4.2.0 表格），`slog.Handler` 只需**一个**（即 4.2.0 的 `slog` 桥）。以下内容仅作历史记录。

参考 `kratos-admin` 的 `internal/server/middleware/logstream/access_filter.go`（教科书级 4 方法实现）：

```go
type xxxHandler struct {
	sink   XXXSink     // 底层 writer / 采集 SDK
	attrs  []slog.Attr // WithAttrs 累积
	groups []string    // WithGroup 累积
}

func (h *xxxHandler) Enabled(_ context.Context, l slog.Level) bool { return l >= h.level }

func (h *xxxHandler) Handle(ctx context.Context, r slog.Record) error {
	m := make(map[string]any, r.NumAttrs()+8)
	m["ts"] = r.Time.Format(time.RFC3339Nano)
	m["level"] = r.Level.String()
	m["msg"] = r.Message
	if r.PC != 0 {
		if fs := runtime.CallersFrames([]uintptr{r.PC}); fs != nil {
			f, _ := fs.Next()
			m["caller"] = fmt.Sprintf("%s:%d", f.File, f.Line)
		}
	}
	for _, a := range h.attrs {                 // WithAttrs 累积的字段
		m[a.Key] = a.Value.Any()
	}
	r.Attrs(func(a slog.Attr) bool {            // 单条记录字段
		m[a.Key] = a.Value.Any()
		return true
	})
	return h.sink.Post(m)
}

func (h *xxxHandler) WithAttrs(as []slog.Attr) slog.Handler {
	n := *h
	n.attrs = append(append([]slog.Attr{}, h.attrs...), as...)
	return &n
}

func (h *xxxHandler) WithGroup(name string) slog.Handler {
	n := *h
	n.groups = append(append([]string{}, h.groups...), name)
	return &n
}
```

逐后端落点：

| 后端 | 文件 | 处理方式 |
|---|---|---|
| `std` | `logger/logger.go` | 内建，`slog.NewTextHandler(os.Stdout)` |
| `zap` | `logger/zap/{zap.go,client.go}` | **改用 `zapslog`**（见下） |
| `logrus` | `logger/logrus/{logrus.go,client.go}` | 实现 `slog.Handler`；`WithAttrs/WithGroup` 返回副本 |
| `zerolog` | `logger/zerolog/{zerolog.go,writer.go,client.go}` | 同上 |
| `fluent` | `logger/fluent/{fluent.go,client.go}` | `Handle` 中 `slog.Record` 摊平为 map 后 `Post` |
| `aliyun` / `tencent` | `logger/{aliyun,tencent}/{*.go,client.go}` | 同上；原 `Logger interface{ log.Logger; GetProducer(); Close() }` → `Handler + Closer` |

`logger/zap` 具体改法：

```go
// 注意：zap v1.27+ 已把 exp 拆成独立模块，需单独 require
//   go get go.uber.org/zap/exp@v0.3.0
// 导入路径不变，仍是 go.uber.org/zap/exp/zapslog
import "go.uber.org/zap/exp/zapslog"

func NewHandler(l *zap.Logger) slog.Handler {
	return zapslog.NewHandler(l.Core(),
		zapslog.WithCaller(true),
		zapslog.AddStacktraceAt(slog.LevelWarn),
	)
}

func init() {
	_ = logger.Register(logger.Zap, func(cfg *conf.Logger) (*slog.Logger, error) {
		zl, err := NewZapLogger(cfg)     // 原有的 core/encoder/lumberjack 组装保持不变
		if err != nil {
			return nil, err
		}
		return kratoslog.NewLogger(NewHandler(zl)), nil
	})
}
```

> 原则提醒：`init()` 注册**只作为扩展手段**；`NewLogger(nil)` 与 `NewLogger(TypeStd)` 必须无注册也能工作（见 4.2.2）。

---

### 4.3 `bootstrap` 模块改造

| 改动点 | v2 现状 | v3 目标 |
|---|---|---|
| `Context.logger` 字段类型 | `kratosLog.Logger` | **项目 `logger.Logger`**（ctx-first，2026-09 已存在，见 §4.2.0） |
| `NewLoggerHelper(module) *log.Helper` | 返回 `*log.Helper` | 替换为 `Logger(module string) logger.Logger`：`return c.logger.With("module", module)` |
| `GetLogger()` | `log.Logger` | `logger.Logger` |
| `NewContextWithParam(..., log log.Logger)` | `log.Logger` | `logger.Logger` |
| `InitAppFunc` / `NewApp` / `RunApp` | `*kratos.App`、`transport.Server` | **不变**（仅 import 换 `/v3`） |
| 全局 logger 安装 | `kratos.Logger(bLogger.NewLoggerProvider(...))` | **双保险**：`logger.SetLogger(x)`（项目级）+ `kratos.Logger(logger.AsSlogLogger(x))`（kratos 边界，见 §4.2.0） |

**提供兼容别名**（降低业务改造量，但标注 deprecated）：

```go
// Deprecated: 用 Logger(module) 代替；等价于 v2 的 NewLoggerHelper。
func (c *Context) NewLoggerHelper(moduleName string) *slog.Logger {
	return c.Logger(moduleName)
}
```

> 这样业务代码里 `l := ctx.NewLoggerHelper("x"); l.Infof("...")` 只需把 `Infof` 改成 `Info`，
> 其余（`l.Error`、`l.Warn`、`l.Debug`）在 slog 下**天然兼容**——是收益最高的一个兼容点。

---

### 4.4 `rpc` 模块改造

`rpc/grpc.go` / `rpc/rest.go` 的差异集中在这几处：

```go
// ---- import 变化 ----
- "github.com/go-kratos/kratos/v2/log"
- "github.com/go-kratos/kratos/v2/middleware/tracing"
- "github.com/go-kratos/aegis/ratelimit"
- "github.com/go-kratos/aegis/ratelimit/bbr"
+ "log/slog"
+ oteltracing "github.com/go-kratos/kratos/contrib/otel/v3/tracing"

// ---- 限流：aegis 整段删除 ----
- var limiter ratelimit.Limiter
- limiter = bbr.NewLimiter()
- mds = append(mds, midRateLimit.Server(midRateLimit.WithLimiter(limiter)))
+ mds = append(mds, midRateLimit.Server())      // v3 内建 bbr，无需 aegis
  // 自定义限流器时才用 WithLimiter（类型别名 ratelimit.Limiter 可结构化实现）

// ---- tracing 中间件 ----
- mds = append(mds, tracing.Server())
+ mds = append(mds, oteltracing.Server(oteltracing.WithTracerProvider(tp)))

// ---- 日志：包级 printf 语义消失 ----
- log.Fatalf("[GRPC] 创建服务端失败: %v", err)
+ slog.Error("[GRPC] 创建服务端失败", "err", err); os.Exit(1)
  // 或更好：直接 return nil, err，由调用方处理

// ---- 结构体字段 ----
- logger *log.Helper
+ logger *slog.Logger
```

`rpc/middleware/{validate,requestid}`：签名（`middleware.Middleware`）与 `middleware.Chain` **完全不变**，只需 import 换 `/v3`。

`rpc/go.mod`：**删除 `github.com/go-kratos/aegis`**；新增 `contrib/otel/v3`。

---

### 4.5 `config` / `registry` / `cache` / `database` / `oss`

**好消息**：接口签名全部未变，属「机械替换」。

| 模块 | 接口是否变化 | 实际改动 |
|---|---|---|
| `config` + 6 个远程配置子模块 | ❌ 不变（`config.Config{Load,Scan,Value,Watch,Close}`、`Source`、`Watcher` 一致） | import `/v3`；包级 `log.Error("...", err.Error())` → `slog.Error("...", "err", err)` |
| `registry` + 8 个注册中心 | ❌ 不变（`Register/Deregister/GetService/Watch`、`*ServiceInstance`） | import `/v3` |
| `cache/redis` | ❌ 不变 | `NewClient(cfg *conf.Data, logger *log.Helper)` → `logger logger.Logger`（**项目接口**，§4.2.0）；`logger.Fatalf` → 返回 error |
| `database/{ent,gorm,mongodb,cassandra,…}` | ❌ 不变 | `log.Logger`/`log.Helper` 参数 → **项目 `logger.Logger`**；`logger.Fatalf` → 返回 error。**注意 `tx7do/go-crud` 的 `WithLogger` 选项**：实测当前为 **`v0.0.53`**，必须用 `go-crud >= v0.0.55`（已去 kratos 依赖），否则整条链被钉在 v2 |
| `oss/{minio,s3}` | ❌ 不变 | `log.Fatal(...)` → `slog.Error(...)` + `os.Exit(1)`；或改成返回 error（推荐） |
| `tracer` / `api` / `ai/*` / `script_engine` | 不依赖 kratos | 仅把 `go` 版本对齐到 `1.25.0`，路径加 `/v3` |

---

### 4.6 `transport/*`：冻结 v2（不发布 v3）——**已定方案**

事实：`tx7do/kratos-transport/transport/{sse,kafka,asynq,mqtt,mcp}@v1.3.8` 的 `go.mod` 里仍是
`github.com/go-kratos/kratos/v2 v2.9.2`。**v3 谱系下无法提供这些模块。**

#### 4.6.1 决策：采用方案 C，本次不迁移

| 方案 | 做法 | 代价 | 结论 |
|---|---|---|---|
| A | 等 tx7do 升级 `kratos-transport` 到 v3 | 不可控，可能无限期 | ❌ 不作为本次路径 |
| B | fork `kratos-transport`，本地 `replace` 升级 | 长期维护成本，且与上游分叉 | ❌ 不投入 |
| **C** | **本次不迁移 `transport/*`**；v3 谱系暂不提供；`gow` 的 v3 模板**不生成**对 `kratos-transport` 的引用（`ServerImportPaths` 遇非 `grpc/rest` 时给出显式报错并跳过） | 功能缺口：v3 项目暂不能用 kafka/sse/mqtt/mcp | ✅ **已定** |

**决策依据（已验证，非假设）**：

1. **现有项目零依赖**：`bear-admin` 全仓搜索 `kratos-transport` / `kratos-bootstrap/transport` 均为 **0 命中**——其服务只用 `grpc` + `rest` 两种 transport，外加 `seata`（应用层事务）与 `eino`（AI 编排），**不涉及任何一种 kratos-transport 传输**。因此冻结 `transport/*` 对本次迁移**不产生任何功能回退**。
2. **阻塞是上游死结**：`kratos-transport@v1.3.8`（截至本文档成稿的最新版）仍钉在 `kratos/v2 v2.9.2`，且无 `/v3` 分支/repo；等待与 fork 都不可控。
3. **不影响 `gow` 主链路**：`gow add service` 默认参数就是 `Servers=["grpc"]`（`gowind/internal/service/cmd.go`），带 `-s rest` 时也只落到 `grpc`/`rest` 两支，**默认路径完全不触碰 `transport/*`**。
4. **成本最低**：省下方案 B 的 fork/移植工时（约 1 人日 + 长期维护），且不阻塞其余全部模块的 v3 迁移。

#### 4.6.2 配套动作（避免「静默降级」）

1. `gow add service x -s kafka` **直接报错并退出**（无 v2 回退，见 §5.0），提示：
   > `kratos-transport` 尚未支持 kratos v3，`kafka/sse/mqtt/mcp/asynq` 传输在 v3 谱系下不可用。
   > `gow` 已不做 v2 兼容，请改用 `grpc` / `rest`，或等待上游支持。
2. `kratos-bootstrap/README` 增加「v3 谱系 transport 支持状态」矩阵，明确哪些模块**不存在于 v3 谱系**；
3. `transport/*/v3` 仅保留**空目录 + README 说明「待上游支持」**，未来上游对齐时可直接补实现，不需要改动模块路径策略；
4. 在 `gow doctor` 中增加一项检查：若生成物引用了 `kratos-transport` 但项目是 v3，报 `FAIL` 并给出上述提示。

#### 4.6.3 未来解锁条件（满足任一即可补做 v3 `transport/*`）

- `tx7do/kratos-transport/transport/*` 的 `go.mod` 出现 `github.com/go-kratos/kratos/v3`；或
- 上游发布 `kratos-transport` 的 `/v3` 模块路径；或
- 出现新的、基于 kratos v3 的 SSE/MCP/消息队列传输实现（可参考 `kratos-admin` 用到的 `contrib/otel/v3` 生态或 `liujitcn/kratos-kit/transport/{sse,mcp}` 的形态，但**不要**复制其全局注册表耦合设计，见 §3）。

---

### 4.7 `kratos-bootstrap` 改动文件总表

| 优先级 | 模块 | 文件（相对 v2 仓） | 改动类型 |
|---|---|---|---|
| P0 | logger | `logger/adapter.go` | kratos 双向桥 → **单个 `slog.Handler` 桥**（项目 `Logger` → `*slog.Logger`）；删 `AsKratosLogger`/`FromKratosLogger`（§4.2.0） |
| P0 | logger | `logger/logger.go` | 去 `log.With`/`DefaultTimestamp`/`DefaultCaller`；`NewLoggerProvider` 返回**项目 `Logger`** |
| P0 | bootstrap | `bootstrap/{context,types,bootstrap,options}.go` | `logger.Logger` 类型 + `Logger(module)`；kratos 边界用 `AsSlogLogger` |
| ✅ **无需改** | logger | `logger/{interface,factory,types}.go`、`logger/{zap,logrus,zerolog,fluent,aliyun,tencent}/*` | **已完成去 kratos 化**（2026-09 实测，勿再重写为 `slog.Handler`） |
| P1 | rpc | `rpc/{grpc,rest}.go` | 去 aegis、tracing 换 contrib/v3、`slog` |
| P1 | rpc | `rpc/middleware/{validate,requestid}/*.go`、`rpc/whitelist.go` | 仅 import |
| P1 | config | `config/{config,factory,bootstrap_config}.go` + `config/*/`（6） | import + `slog` |
| P1 | registry | `registry/{discovery,registrar}.go` + `registry/*/`（8） | import |
| P1 | cache | `cache/redis/redis.go` | `*slog.Logger` |
| P1 | database | `database/{ent,gorm,mongodb,cassandra,clickhouse,doris,elasticsearch,influxdb,opensearch}/client.go` | `slog` + go-crud 升版 |
| P2 | oss | `oss/minio/client.go`、`oss/s3/{client,storage}.go` | `slog` |
| P2 | api / tracer / ai / script_engine | 全部 | 仅 `go` 版本 + 模块路径 |
| — | transport | 不迁 | — |
| 新增 | 全仓 | 每个子模块目录复制为 `…/v3` 变体（或建 `v3/` 分支） | 模块路径 + tag |

---

### 4.8 未发布场景：生成的项目怎么拿到「本地改过的」kratos-bootstrap

> **场景**：`kratos-bootstrap` 的 v3 谱系**不会发布到公开 proxy**（本地先改、先验证），
> 但 `gow new` 生成的项目、以及 `bear-admin` 必须能**直接编译并使用本地这份源码**。
> 本节给出完整答案与操作命令。

#### 4.8.1 前提：模块路径必须先落到终态

无论用哪种方案，**本地目录里的 `go.mod` 的 `module` 行必须等于项目要 import 的路径**。
Go 的「目录替换 / workspace」不做路径重写，只做「路径 → 目录」的绑定：

```go
// kratos-bootstrap/bootstrap/go.mod（本地，v3 谱系）
module github.com/tx7do/kratos-bootstrap/bootstrap/v3   // ← 必须是终态路径
go 1.25.0
```

因此 §7 阶段 1「改 `module` 行」是**所有本地联调方案的先决条件**，不能留到最后做。

**为什么在「不发布」的前提下仍然要加 `/v3` 后缀？** 两个实际收益：

1. **fail-fast**：如果漏 `replace` 了某个子模块，Go 会直接报
   `no required module provides package …/logger/v3`；若不加后缀，则会**静默从 proxy 拉 v2 版本**，
   于是 `kratos/v2` 与 `kratos/v3` 同时进入依赖图 → 一堆莫名其妙的类型不匹配。
2. **发布即终态**：等真的发布时，路径不需要二次改名，删掉 `replace` / `go.work` 即可上线。

#### 4.8.2 三种方案对比

| | 方案 A：`go.work` | 方案 B：`go.mod` 目录 `replace` | 方案 C：伪发布（私有 proxy / 本地 tag） |
|---|---|---|---|
| 本地代码如何被拿到 | Go 直接读磁盘目录（`use`） | Go 直接读磁盘目录（`=> 路径`） | 从私有 proxy / 本地 git 拉 |
| 需要网络 | ❌ 不需要 | ❌ 不需要 | ✅ 需要（走 GOPROXY） |
| 需要发布/tag | ❌ 不需要 | ❌ 不需要 | ✅ 需要打 tag |
| 改动 `go.mod` | ❌ 不改（只在项目根加 `go.work`） | ✅ 要加 `require`（占位版本）+ `replace` | ✅ 改版本号 |
| 能否提交共享 / CI 能用 | ❌（`go.work` 已 gitignore，见下） | ✅（相对路径，可提交） | ✅ |
| 每人布局可不同 | ✅ | ❌（相对路径需约定布局） | ✅ |
| **推荐用途** | **个人本地联调（主推）** | **提交 / 同事共享 / CI（主推）** | **"伪发布"给多仓/多机用时** |

> **结论：A + B 组合使用**。日常联调用 A（零 `go.mod` 污染）；需要提交或跑 CI 时用 B。
> `bear-admin` 现在用的就是 B（`replace github.com/tx7do/kratos-bootstrap/registry/nacos => ../../../go/wind/kratos-bootstrap/registry/nacos`），
> 而它的 `.gitignore` 第 38 行**已经忽略了 `go.work`**——说明 A 这条路本来就是预留好的。

#### 4.8.3 方案 A：`go.work`（个人本地联调，推荐）

**建议目录布局**（把三个仓放在**同一父目录**，相对路径只有一层，避免 `bear-admin` 现在那种 `../../../go/wind/...` 的深层路径）：

```
E:/project/my-biz/go/
├── kratos-bootstrap/            ← v3 谱系本地源码（本方案要改的仓）
│   ├── api/  bootstrap/  config/  logger/  registry/  rpc/  tracer/  database/  oss/  cache/
├── go-wind-toolkit/gowind/      ← gow 源码
├── go-wind-admin-template/      ← 模板仓（gow new 用；本地联调时也给 gow 指向它）
└── your-project/                ← gow new 生成出来的项目
    ├── go.work                  ← 方案 A（本文件已 gitignore）
    └── go.mod
```

**`gow` 自动生成的 `your-project/go.work`**：

```go
go 1.25.0

use (
	.

	// ---- 本地 kratos-bootstrap v3（按项目实际用到的最小集列出）----
	../kratos-bootstrap/api
	../kratos-bootstrap/bootstrap
	../kratos-bootstrap/config
	../kratos-bootstrap/config/nacos
	../kratos-bootstrap/logger
	../kratos-bootstrap/logger/fluent
	../kratos-bootstrap/registry
	../kratos-bootstrap/registry/nacos
	../kratos-bootstrap/rpc
	../kratos-bootstrap/tracer
)
```

**操作流程（纯离线，无网络）**：

```powershell
cd E:/project/my-biz/go/your-project

# 1) 让 gow 按当前依赖集生成 go.work（见 §5.5）；手工等价做法：
go work init .
go work use ../kratos-bootstrap/bootstrap ../kratos-bootstrap/logger ...   # 按需

# 2) 把 workspace 的版本回写进各模块 go.mod（补齐 require / 统一版本）
go work sync

# 3) 离线构建验证
$env:GOFLAGS = "-mod=mod"
go build ./...

# 4) 关键：验证「去掉 workspace 后」还能不能构建（= 同事/CI 视角）
$env:GOWORK = "off"
go build ./...     # 此时必然失败，除非 go.mod 里有方案 B 的 replace —— 这正是方案 B 的意义
Remove-Item Env:\GOWORK
```

要点：
- `use` 里**只能列目录**（该目录下必须有 `go.mod`）；模块路径取自各目录的 `go.mod`，不由 `go.work` 指定。
- `go.work` 只解决「本地怎么解析」；**不会**给各模块补 `require`，需要 `go work sync`。
- `go.work` / `go.work.sum` **不要提交**（不同人布局不同，一提交 CI 就会用错路径）。

#### 4.8.4 方案 B：`go.mod` 目录 `replace`（提交 / 共享 / CI）

`gow` 在生成项目的 `go.mod` 里同时写入 **`require`（占位版本 = 未来真实 tag）** 与 **注释化的 `replace` 块**：

```go
require (
	github.com/go-kratos/kratos/v3                    v3.0.0
	github.com/tx7do/kratos-bootstrap/api/v3          v3.0.0   // ← 占位版本，未来真实 tag
	github.com/tx7do/kratos-bootstrap/bootstrap/v3    v3.0.0
	github.com/tx7do/kratos-bootstrap/config/nacos/v3 v3.0.0
	github.com/tx7do/kratos-bootstrap/logger/v3       v3.0.0
	github.com/tx7do/kratos-bootstrap/registry/nacos/v3 v3.0.0
	github.com/tx7do/kratos-bootstrap/rpc/v3          v3.0.0
)

// ---- 本地联调：kratos-bootstrap v3 尚未发布，指向本机源码 ----
// 发布后删除整个 replace 块 + 执行 go mod tidy 即可（require 里的 v3.0.0 就是发布版本，无需改动）
replace (
	github.com/tx7do/kratos-bootstrap/api/v3            => ../kratos-bootstrap/api
	github.com/tx7do/kratos-bootstrap/bootstrap/v3      => ../kratos-bootstrap/bootstrap
	github.com/tx7do/kratos-bootstrap/config/v3         => ../kratos-bootstrap/config
	github.com/tx7do/kratos-bootstrap/config/nacos/v3   => ../kratos-bootstrap/config/nacos
	github.com/tx7do/kratos-bootstrap/logger/v3         => ../kratos-bootstrap/logger
	github.com/tx7do/kratos-bootstrap/registry/v3       => ../kratos-bootstrap/registry
	github.com/tx7do/kratos-bootstrap/registry/nacos/v3 => ../kratos-bootstrap/registry/nacos
	github.com/tx7do/kratos-bootstrap/rpc/v3            => ../kratos-bootstrap/rpc
	github.com/tx7do/kratos-bootstrap/tracer/v3         => ../kratos-bootstrap/tracer
)
```

要点：
- **`require` 必须存在**（哪怕版本还没发布）；目录替换会让 Go **完全不去校验、不去下载**这个版本。
  `bear-admin` 现有的 `replace` 就是「左侧不带版本」（等价于替换所有版本），可以照抄这个写法。
- **右侧目录的 `go.mod` 必须声明与左侧完全一致的 `module` 路径**（即 §4.8.1）。
- 右侧用**相对路径**，且约定「项目与 kratos-bootstrap 同级」；不要写 `E:/...` 绝对路径（同事/CI 必崩）。
- 一处漏 `replace` → 该子模块会从 proxy 拉 **v2 版**，并把 `kratos/v2` 拖进依赖图。故 `gow` 要
  **按项目实际 import 生成完整列表**，并在 `gow doctor` 中加一项「replace 覆盖率」检查。
- 校验：
  ```powershell
  go mod verify                       # 校验已下载模块（本地 replace 的不在校验范围）
  go list -m all | Select-String "kratos/v2"    # 必须 0 命中
  ```

#### 4.8.5 方案 C：伪发布（多机 / 多仓 / 需要「版本化」时）

仅当需要「不公开但要按版本引用」（例如 CI 机器、同事机器不方便放源码）时才用：

```powershell
# 方式 1：私有 proxy（推荐）
$env:GOPROXY   = "https://your-private-proxy,direct"
$env:GOPRIVATE = "github.com/tx7do/*"
# 在 kratos-bootstrap 各子模块目录里打 tag（注意 /v3 子模块的 tag 形如 logger/v3/v3.0.0）
git tag logger/v3/v3.0.0
git push origin logger/v3/v3.0.0

# 方式 2：本地目录当 proxy（完全离线）
go mod download all                     # 在 kratos-bootstrap 里预热
$env:GOPROXY = "file://E:/go-proxy-cache"
$env:GONOSUMDB = "*"; $env:GOFLAGS = "-mod=mod"
```

**代价**：每次改本地代码都要重新打 tag + 刷新 cache，迭代很慢 → **仅作兜底**。
`/v3` 子模块的 tag 规则要特别注意：`github.com/tx7do/kratos-bootstrap/logger/v3` 的 tag 是 `logger/v3/v3.0.0`（不是 `v3.0.0`）。

#### 4.8.6 反模式（务必避免）

| 反模式 | 后果 |
|---|---|
| ❌ `go mod vendor` | 本地代码被**快照固化**，改了 kratos-bootstrap 不生效（除非反复 re-vendor），还会掩盖 replace 遗漏 |
| ❌ 只 `replace` 一部分 kratos-bootstrap 子模块 | 未 replace 的从 proxy 拉 **v2 版** → `kratos/v2` 与 `kratos/v3` 同图 |
| ❌ `replace` 右侧写绝对路径 `E:/project/...` | 同事/CI 直接崩；也破坏「可提交」这一核心价值 |
| ❌ 提交 `go.work` / `go.work.sum` | CI 与同事被强制使用你的目录布局 |
| ❌ 只做 `GOWORK` 模式验证，从不跑 `GOWORK=off` | 上线/CI 时才发现 `require`/`replace` 缺失 |
| ❌ 用 `v0.0.0-00010101000000-000000000000` 之类当 `require` 占位版本 | 发布时 `go mod tidy` 会把版本号改乱；**占位版本直接用未来的真实 tag（`v3.0.0`）** |
| ❌ 忘了 `kratos-bootstrap/**/go.mod` 的 `module` 行尚未改 `/v3` | `replace` 报 `module declares its path as X but was required as Y` |

#### 4.8.7 发布时的无痛切换（验证「今天是可发布的」）

```powershell
# 1) 删除 go.work / go.work.sum（本来就是 gitignore 的）
# 2) 删除 go.mod 里的 replace ( ... ) 块
# 3) 拉取真实发布版本
go get github.com/tx7do/kratos-bootstrap/bootstrap/v3@v3.0.0
go mod tidy
# 4) 断言依赖图里没有 kratos/v2
go list -m all | Select-String "kratos/v2"     # 期望：无输出
```

因为 §4.8.4 的 `require` 占位版本**就是**未来的真实 tag，这一步只做「删 replace」，
**不需要改任何 `require` 版本号**——这就是选择「占位版本 = 真实版本」的原因。

---

## 5. `gow`（`go-wind-toolkit/gowind`）改造方案

### 5.0 决策：`gow` 不做 v2 兼容，直接 v3 单一输出（已定）

> 本节记录本次政策变更，它是 §5.1–§5.5 的前提。

**决策**：`gow` 只产出 kratos v3 项目，**不提供** `--kratos-version=2` 之类的回退开关。

**落地口径**：

1. **模板硬编码**：`pkg/generators/templates/golang/*.tpl` 直接写 `github.com/go-kratos/kratos/v3`、`github.com/tx7do/kratos-bootstrap/*/v3`、`log/slog`；**不引入** `{{.KratosImport}}` 之类抽象层，也不保留 v2 分支。
2. **无开关**：`gow new` / `gow add service` 不新增 `--kratos-version` 参数，`cmd.go` 不做版本透传。
3. **不可用的传输直接失败**：`-s kafka|sse|mqtt|mcp|asynq|...` 在 v3 谱系无实现（§4.6），`ServerImportPaths` 遇到非 `grpc`/`rest` **返回 error**，命令报错退出。
4. **生成目标固定**：产出项目的 `go` 版本固定 `1.25.0`，依赖版本集中在 `pkg/generators/deps.go`（§5.3.4），其中 `go-crud >= v0.0.55`。

**收益（相对原双版本方案）**：

- 省掉 `pkg/generators/kratosver.go` 抽象、`--kratos-version` 参数透传、v2 模板分支与对应的双份测试用例（约 **0.5d**）；
- 模板可读性更高，不存在「抽象层前缀猜错」类 bug；
- 与 §4.6「v3 谱系不发布 `transport/*`」天然自洽：没有 v2 回退，也就不存在「静默降级」的诱惑。

**边界说明**：本决策**只约束 `gow` 的产出**。`kratos-bootstrap` 的 v2 谱系是否保留，属 §4.1 的独立决策（见 §11 待拍板 #1），不受此条影响——即 v2 谱系仍可存在以服务 `bear-admin` 等存量项目，但 `gow` 不再为它生成代码。

**为什么可以这样定（依据）**：

- `gowind/go.mod` **不依赖 kratos**（全仓 0 处 `go-kratos/kratos`），`gow` 只是「代码生成器 + 模板仓库拉取器」，其 v3 化 = 模板文本替换 + 依赖版本常量，风险低；
- `gow new` 的本质是**克隆 `go-wind-admin-template` 再 `go mod tidy` + `buf generate`**（`internal/project/cmd.go`），项目的 v3 属性主要由**模板仓**（§6）决定，`gow` 侧无需版本开关；
- 「双版本模式」在原方案中本就是**待新增功能**（`--kratos-version` / `KratosVersion` 在 `gowind` 全仓 0 命中），删掉它是**减法**而非返工。

### 5.1 修掉 `skill.md` 的 10 个问题（根因 → 落点）

根因：`gow add service` 走 `pkg/service`（只生成 server/main/wire 骨架 + 3 份 wire_set，**不生成 client/repo/service 实现，也不跑 wire**），而 `gow generate` 走 `pkg/sqlkratos`（另一套约定，**且 wire_set 前缀是对的**）。两条链路约定不一致。

| # | `skill.md` 症状 | 根因（已定位） | 落点修复 |
|---|---|---|---|
| 1 | `wire_gen.go` 缺失 → `initApp` 未定义 | `pkg/service` 从不执行 wire | `pkg/service.Generate()` 末尾调用 `internal/wire.RunGenerate`；失败时生成 `wire_gen.go` 骨架并打印明确指引（不再静默） |
| 2 | `rest_server.go` 引用不存在的 auth 包 | `rest_server.tpl` 硬编码 `kratos-authn`/`kratos-authz` + `{module}/pkg/middleware/auth` | 默认**只生成 `logging.Server` + `recovery.Recovery`**；auth 作为 `--with-auth` 可选分支；且 auth 中间件只从 `kratos-bootstrap/rpc/middleware/auth` 取（不再依赖模板仓 `pkg/middleware/auth`） |
| 3 | `data/providers/wire_set.go` 引用不存在的 `data.NewEntClient` | `pkg/service.generateDataPackageCode` 硬编码 `data.` 前缀，但 `ent_client.tpl` 声明 `package client`（路径 `internal/data/client`） | 改为 `client.NewEntClient` + `ExtraImports=["data/client"]`（对齐 `pkg/sqlkratos/render.go:WriteDataWireSetCode` 的正确写法） |
| 4 | `service/providers/wire_set.go` 为空 | 调用处传 `services=[]string{}` | 从 proto/表结构推断 `New<Model>Service`；无 proto 时生成**带 `// TODO` 的占位构造器**并生成对应 `internal/service/<model>_service.go` |
| 5 | `server/providers/wire_set.go` 缺中间件构造器 | `generateServerPackageCode` 只注入 `New<X>Server` | postfix 增加一组：`NewRestMiddleware`/`NewGrpcMiddleware`（wire 才能真正装配中间件） |
| 6 | 缺 `openapi.yaml`（`//go:embed` 失败） | `assets.tpl` embed 了它，但从不生成 | `writeAssets` 同时写出 `openapi.yaml` 最小骨架（`openapi: "3.0.0" / paths: {}`），后续由 `buf generate` 覆盖 |
| 7 | 缺 `go-crud` / `kratos-bootstrap/database/ent` 依赖 | 模板引用但生成器不写依赖 | 生成完成后自动执行 `go get`（版本表见 §5.3），或至少把依赖清单写入生成日志 |
| 8 | `data/` 无源文件 | `generateDataPackageCode` 只写 wire_set | 真正生成 `internal/data/client/{ent_client,redis_client,gorm_client}.go`（`package client`）|
| 9 | 无 ent schema | `add service` 不建 schema | 生成 `internal/data/ent/schema/<model>.go` 最小骨架 + 自动跑 `gow ent` |
| 10 | `main.go` 未用 import 挡住 wire | `main.tpl` 里大量注释残留、按 `Servers` 注入 import 时可能不匹配 | 模板**只输出真正用到的 import**（由 `ServerImportPaths` 驱动，删掉注释残留）；生成后统一跑 `gofmt` + 未用 import 检测 |

> **2026-09 实测补充（对 `skill.md` 10 项的复核）**：
> - **新增事实**：`pkg/service` 现已支持**手写装配**（`GenerateWiring` + `wiring.tpl` + `BuildWiringBlocks`，非 `--wire` 时默认）与 wire 两种模式。原文「只生成 wire 骨架」的描述**已过时**，但两种模式**都必须 v3 化**。
> - **#3 根因确认仍在**：`pkg/service/service.go` 的 `writeWireSetFunctionCode` 生成 `client.NewXxxClient` 却**不传 `ExtraImports`**；而 `pkg/sqlkratos/render.go` 的 `WriteDataWireSetCode` 会补 `data/client`（两者差异已确认）。
> - **`redis_client.tpl` 的 3 处编译错误确认仍在**（缺 `bootstrap` import、`return nil` 少 2 个返回值、闭包用未声明的 `l`）。
> - **#6 / #9 / #10 均未修复**，仍然有效。
> - **`gow ent add` 已修复**（见 §5.3 第 6 条），从待办中移除。

### 5.2 模板 v3 化清单（实测：15 个模板，**12 个命中 `kratos/v2`**）

> 2026-09 实测：`pkg/generators/templates/golang/` 共 **15 个 `.tpl`**（原文写 12）。其中 `assets.tpl`、`wire_set.tpl`、`gorm_init.tpl` **不含任何 kratos 引用**，无需改动。

| 文件 | 实测命中 | 改动 |
|---|---|---|
| `ent_client.tpl` | `kratos/v2` L4；kb L14,15；**`log.Fatalf` L54** | `log`→`slog`；修 L54 包级 `log.Fatalf`（改为返回 error）；**补 `bootstrap` import**；`entBootstrap` 加 `/v3` |
| `ent_repo.tpl` | `kratos/v2` L7；kb L8；`log *log.Helper` L31 | `/v3`；`*log.Helper` → **项目 `logger.Logger`** |
| `gorm_repo.tpl` | `kratos/v2` L7；kb L8；`log *log.Helper` L24 | 同上 |
| `service.tpl` | `kratos/v2` L6；kb L7；`log *log.Helper` L29 | 同上 |
| `grpc_server.tpl` | `kratos/v2` L4-6；kb L8,9 | `/v3`；中间件构造器签名与 logger 类型 |
| `rest_server.tpl` | `kratos/v2` L4-6；kb L10,11 | 去 auth 硬耦合；`/v3`；mock 语义修正 |
| `grpc_client.tpl` | `kratos/v2` L4；kb L5 | `/v3` |
| `gorm_client.tpl` | kb L6,7 | 修 `return nil, err` 错位；加 `/v3` |
| `redis_client.tpl` | `kratos/v2` L4；kb L7,8 | **修 3 处编译错误**：缺 `bootstrap` import、`return nil`（应为 3 值）、闭包用未声明的 `l` |
| `main.tpl` | `kratos/v2` L6；kb L10,11,**L13–36** | `/v3`；**删除 L13–36 的注释残留 import** |
| `wire.tpl` | `kratos/v2` L19；kb L20 | `/v3` |
| **`wiring.tpl`**（**原文遗漏**） | `kratos/v2` L4；kb L5 | `/v3`；**手写装配模式（非 `--wire` 时默认）必须同步改，否则主链路仍产 v2 代码** |
| `assets.tpl` / `wire_set.tpl` / `gorm_init.tpl` | 无 kratos 引用 | 无需改动 |

**同时必须同步的非模板文件**：

| 文件 | 实测 | 改动 |
|---|---|---|
| `pkg/generators/utils.go` | `ServerImportPaths` L84,89 为 `kratos/v2/transport/{grpc,http}`；另有 **25 个 `kratos-transport/transport/*` case**（L94–213）；同文件还有 `ServerFormalParameters`(L220-360)、`ServerTransferParameters`(L363-503) | grpc/rest → `/v3`；非 grpc/rest **一律返回 error**（§5.0）；三个函数需一致处理 |
| `pkg/generators/utils_test.go` | L307,312,318,326 断言 v2 路径；L333-355 断言 kratos-transport | 期望改 `/v3`；**删除 transport 期望用例** |

> `redis_client.tpl` 当前生成物**必然编译失败**（`return nil` 少两个返回值 + 闭包里用了未声明的 `l`），
> 这是 `skill.md` 未列出但优先级最高的一类问题——生成器必须**默认产出可编译代码**。

### 5.3 新增能力（让「生成即可编译」可验证）

1. **单一 v3 输出**（不做 v2 兼容，见 §5.0）：模板**直接硬编码** `kratos/v3` 与 `kratos-bootstrap/*/v3`，**不引入** `--kratos-version` 开关，也不做 `{{.KratosImport}}` 抽象。
   - 产出项目 Go 版本固定 `1.25.0`；依赖版本集中在 `pkg/generators/deps.go`（见下第 4 条），其中 `go-crud >= v0.0.55`。
2. **生成后自动校验**：
   ```
   gow add service user -s rest -s grpc -d ent -d redis
     → 生成 → go mod tidy → wire → gofmt → go build ./app/user/service/...
   ```
   任一步失败，**回滚本次生成的文件**并打印定位信息（而不是留下半成品）。
3. **`gow doctor`**：把 `skill.md` 的 10 项变成可执行体检项，逐条输出 `PASS/FAIL` + 修复建议。
4. **依赖版本集中管理**（`pkg/generators/consts.go` 或新增 `deps.go`）：
   ```go
   const (
     KratosV3              = "github.com/go-kratos/kratos/v3@v3.0.0"
     KratosBootstrapV3Root = "github.com/tx7do/kratos-bootstrap/bootstrap/v3@v3.0.0"
     GoCrudEntgoMin        = "github.com/tx7do/go-crud/entgo@v0.0.55"   // < v0.0.55 会拉回 kratos v2
     ...
   )
   ```
5. **`replace` 注释块 + `go.work`**：生成的项目 `go.mod` 末尾写入注释化的本地 `replace` 模板，
   并在项目根生成 `go.work`，让用户「改 kratos-bootstrap 一行生效」——**完整设计见 §4.8 与 §5.5**：
   ```go
   // 本地联调：kratos-bootstrap v3 尚未发布，指向本机源码（发布后删除整个 replace 块）
   // replace github.com/tx7do/kratos-bootstrap/bootstrap/v3 => ../kratos-bootstrap/bootstrap
   ```
6. ~~**`gow ent add` 修复**：`internal/ent/cmd.go` 里 `entCmd.AddCommand(...)` 被注释掉了~~ → **已修复，无需再改**（2026-09 实测：`cmd/gow/ent.go:38` 的 `entCmd.AddCommand(entGenerateCmd, entAddCmd)` **未被注释**，`ent add` 已可用；原文所指的「`internal/ent/cmd.go` 被注释」已不成立）。
7. **既有命令体系已扩容**（实测）：除 `new/project`、`add/service`、`ent`、`wire`、`run`、`version` 外，新增 **`api`（buf 生成，非 `buf` 命令名）、`build`、`migrate`、`generate`、`extract`**。`gow` 的 v3 化需确认这些命令产出的脚手架/配置是否同样硬编码了 kratos v2 路径。
8. **`pkg/frontendgen/` 与 kratos 无关**（实测）：它把 OpenAPI 规格转成 Vue/React 前端代码（`FrameworkVueVben`/`VueElement`/`ReactAntd`），输入是 OpenAPI YAML，**全目录 0 处 kratos 引用** → v3 迁移**不涉及**该目录。

### 5.4 `gow` 改动文件总表

| 文件 | 改动 |
|---|---|
| `pkg/service/service.go` | 修 #3/#4/#5/#8（含 `writeWireSetFunctionCode` 补 `ExtraImports`）；末尾接 wire + build 校验 |
| `pkg/service/`（手写装配链路） | `GenerateWiring` / `BuildWiringBlocks` / `wiring.tpl` 的 v3 化（**原文未覆盖**） |
| `pkg/generators/utils.go` | `ServerImportPaths` / `ServerFormalParameters` / `ServerTransferParameters` **直接输出 v3 路径**；非 `grpc`/`rest` 一律返回 error（对应 §4.6.2 配套动作 1：`kratos-transport` 在 v3 谱系已冻结；无 v2 回退） |
| `pkg/generators/go_generator.go` | `GenerateWireSet` 的 `ExtraImports` 在 `pkg/service` 链路也生效 |
| `pkg/generators/deps.go`（新增） | 依赖版本常量集中（kratos v3 / bootstrap v3 / go-crud ≥ v0.0.55） |
| `pkg/generators/templates/golang/*.tpl`（**15**，12 命中 v2） | §5.2 |
| `pkg/generators/templates/yaml/server_yaml.tpl` | 补 `rest.middleware` 段、`rest.addr`/`grpc.addr`、cors；去掉 auth 段 |
| `internal/service/cmd.go` | 新增 `--with-auth`、`--verify` 参数（**不新增** `--kratos-version`，见 §5.0） |
| `internal/wire/cmd.go` | 支持在生成流程内被调用（抽 `Generate(serviceRoot)` 可复用函数） |
| `cmd/gow/*.go` | 新命令 `doctor` / `work` / `deps`（**无** `--kratos-version` 透传）；核对既有 `api`/`build`/`migrate`/`generate`/`extract` 的 v3 产物 |
| `go.mod` | `go 1.25.x`（实测已为 `1.25.5`，无需改动） |
| `pkg/generators/utils_test.go` 等测试 | 期望路径改 `/v3`；**删除 v2 期望用例**，不再做双版本断言 |
| `internal/ent/cmd.go`、`pkg/frontendgen/` | **无需改动**（前者已修复，后者与 kratos 无关，见 §5.3） |

### 5.5 `gow` 的依赖装配能力（对应 §4.8）

`gow` 要把「未发布时怎么拿到本地代码」变成**一条命令**，而不是让用户手工拼 `replace`。

> ⚠️ **实测（2026-09）**：`gowind` 全仓**不存在**任何 `go.work` / `replace` / `go mod edit` 的**写入**逻辑（仅在 `internal/service/cmd.go` 的文案与 `internal/run/watch.go` 的告警字符串中出现字样）。`internal/project/project.go` 的项目创建流程为：**克隆模板 → 字符串替换 module 名 → `go mod tidy` → `buf generate`**，全程不触碰 `replace`/`go.work`。
> 因此本节**整节都是新增工作**，无历史包袱，也无「已有实现需改造」的连带风险。

| 新命令 / 参数 | 作用 |
|---|---|
| `gow new <name> --bootstrap-local <path>` | 生成项目时，`go.mod` 直接写入**完整的 `require` + 注释化 `replace` 块**（`§4.8.4` 的形态），并生成 `go.work`（`§4.8.3`） |
| `gow new <name> --template-local <path>` | 不克隆远端模板仓，直接用本地 `go-wind-admin-template`（改模板 + 立刻验证的闭环） |
| `gow work`（新增） | 补全/刷新项目根的 `go.work`：扫描项目实际 import 的 `kratos-bootstrap/*/v3`，把对应**本地目录**写进 `use (...)`；再执行 `go work sync`。**幂等**，可反复执行 |
| `gow deps`（新增） | 输出「项目引用的每个 `kratos-bootstrap` 模块 → 本地目录 → 是否已 `replace`」对照表；**缺项即报错**，专治 §4.8.6 的「漏 replace 静默拉 v2」 |
| `gow add service ... --verify` | 生成后链路：`go mod tidy → gow work → wire → gofmt → go build`；失败回滚（`§5.3.2`） |

**`gow` 生成 `go.work` 的算法（简版）**：

```
1. 解析 go.mod 中所有 require github.com/tx7do/kratos-bootstrap/... 的项
   （含 indirect —— indirect 也要 use，否则同样会去 proxy 拉 v2）
2. 对每个模块路径去掉 github.com/tx7do/kratos-bootstrap/ 前缀，映射到本地
   <bootstrapLocal>/<相对路径>
   + 特例：.../api/v3 → <bootstrapLocal>/api（子模块内部路径与 /v3 后缀不总是一一对应，
     以「本地目录的 go.mod 声明的 module 路径」为准做**反查**，而不是纯字符串推导）
3. 写 go.work（use 列表去重 + 排序），保留用户手改过的块
4. 执行 go work sync
```

> 第 2 步的「反查」很重要：`github.com/tx7do/kratos-bootstrap/config/nacos/v3` 对应本地
> `config/nacos/`，而 `github.com/tx7do/kratos-bootstrap/logger/v3` 对应 `logger/`。
> 纯字符串删后缀可行，但**必须校验**目标目录 `go.mod` 的 `module` 行是否等于该路径，
> 否则就是 §4.8.6 里的 `module declares its path as X but was required as Y` 错误。

**`gow doctor` 新增检查项**（与 §5.3.3 的 10 项并列）：

| 检查项 | 判据 |
|---|---|
| `replace` 覆盖率 | `go.mod` 中所有 `kratos-bootstrap/.../v3` 的 require，都必须有对应 `replace`（或 `go.work` 的 `use`） |
| 无 v2 残留 | `go list -m all \| grep "go-kratos/kratos/v2"` 为空 |
| `module` 行一致 | 每个本地目录 `go.mod` 的 `module` 行 == 被 require 的路径 |
| 无绝对路径 | `go.mod` 的 replace 右侧不含盘符（`^[A-Za-z]:`）或根路径 |
| 占位版本合法 | `require .../v3` 的版本是 `v3.x.x` 形式（不是 `v0.0.0-...`） |
| workspace 一致性 | `go.work` 的 `use` 列表覆盖所有 require（若存在 `go.work`） |

---

## 6. `go-wind-admin-template`（`gow new` 克隆的模板仓）改造

> 这是「生成物里 admin 模块」的来源，**2026-09 已下载到本工作区**（`go-wind-admin-template/`），必须同步，否则 `gow new` 出来的项目仍是 v2。
>
> 实测现状：`go 1.25.4`、**kratos 仍 v2.9.2**；结构为 `api/ app/ pkg/ script/ app.mk Makefile tag.sh`。

| 项 | 改动 |
|---|---|
| `go.mod` | 实测仍 `kratos v2.9.2` → 升 `/v3`；`kratos-bootstrap/*` → `*/v3`；`go-crud >= v0.0.55`；删除 `kratos-authn`（被阻塞）或降为可选 |
| `api/buf.gen.yaml` / `buf.admin.*.gen.yaml` | 插件路径升 `/v3`（`protoc-gen-go-http/v3`、`protoc-gen-go-errors/v3`）；`Merrors/errors.proto` 映射到 `kratos/v3/errors` |
| `Makefile` 的 `plugin`/`cli` 目标 | `protoc-gen-go-http/v3`、`protoc-gen-go-errors/v3`；`protoc-gen-go-http` 版本与 kratos v3 对齐 |
| `api/gen/go/**/*_http.pb.go` | 全部**重新生成**（`SupportPackageIsVersion1` → `SupportPackageIsVersion3`） |
| `api/gen/go/**/*_errors.pb.go` | 无需重生成（`errors` 仍是 `SupportPackageIsVersion1`） |
| `app.mk` | `wire` 目标不变；新增 `v3` 模式下的 `build → verify` 步骤 |
| 业务代码 | `log.Helper` → **项目 `logger.Logger`**；`ctx.NewLoggerHelper(x).Infof(...)` → `ctx.Logger(x).Info(...)`（§4.3） |
| `main.go` | 删除对 `registry` 等未用 import；**不需要**显式注册 codec（见 §1） |
| 端口/配置 | `server.yaml` 的 admin 与业务服务端口不冲突；`rest.middleware` 段齐全 |

---

## 7. 分阶段实施计划

| 阶段 | 内容 | 产出 | 工时 |
|---|---|---|---|
| **0** | 决策冻结：`/v3` 谱系 vs 单谱系；**Go 抬升 11 个低于 1.25.0 的模块**（§4.1；其余 45 个已达标，无需下压）。**同时敲定本地联调约定**：三个仓同级布局 + `go.work` gitignore 策略 + `replace` 相对路径约定（§4.8.3 / §4.8.4） | 决策记录 + 分支 + 约定文档 | 0.5d |
| **1** | `kratos-bootstrap` 骨架：**先把每个子模块 `go.mod` 的 `module` 行改到 `/v3` 终态路径**（这是所有本地联调方案的先决条件，见 §4.8.1），再切 `kratos/v3@v3.0.0` + Go 1.25.0，只做「import 机械替换」，不碰 logger。**产出即验证**：在 `your-project` 里 `go work use` 一次，确认能离线解析本地模块 | 可编译的空壳（logger 暂用临时 std）+ 可联调的 workspace | 1.5d |
| **2** | `logger` 桥接 + `bootstrap.Context`：`adapter.go` 换**单个 `slog.Handler` 桥**（项目 `Logger` → `*slog.Logger`）；`logger.go` 去 `log.With`/`DefaultTimestamp`/`DefaultCaller`；6 后端**无需改**（§4.2.0）；`Context` 用 `logger.Logger` + `Logger(module)` | `logger/v3` 全绿 + 单测 | **0.5d** |
| **3** | `rpc`：去 aegis、`oteltracing`、`slog`；`config`/`registry`/`cache`/`database`/`oss`：import + `slog` | 全部子模块 `go build ./...` 通过 | 2.5d |
| **4** | `gow`：模板 v3 化（12 文件，**直接 v3、无兼容分支**，见 §5.0）+ 修 `skill.md` 10 项 + 生成后自动 wire/build | `gow add service` 端到端通过 | 2.5d |
| **5** | 模板仓 `go-wind-admin-template`：buf/Makefile/`*_http.pb.go` 重生成/业务 `slog` | `gow new` 出来的项目 `go build ./...` 通过 | 2.0d |
| **6** | `gow doctor` + 文档 + 迁移脚本（`kratos-bootstrap` v2→v3 的 sed 规则表） | 交付文档 | 1.0d |
| **7** | 回归：`bear-admin` 作为真实样本走一遍迁移 | 迁移报告 | 1.0d |
| | | **合计** | **≈ 11.5d** |

> 若阶段 1 采用「单谱系破坏式升级」，可省 0.5d，但会失去 v2 并存能力，且 `transport/*` 需另设分支——不推荐。

### 强烈建议的执行顺序理由

先做 **阶段 1 + 2**。因为：
- `logger` 仍是全仓的**类型源头**（`Context.GetLogger()` 被所有模块调用），桥一换，其余模块才好动；
- 但 logger 的改造量**已从 3.5d 降到 0.5d**（§4.2.0：只需换 `adapter.go` 一个 `slog` 桥 + 清理 `logger.go`）——**它已不再是风险点**；
- 阶段 1 的「import 机械替换」可让 v3 骨架**先编译起来**，使后续每一步都有可编译基线。

> ⚠️ **修订**：原文「logger 是唯一无法机械替换的模块（需重写 6 个 handler）」**已不成立**。当前真正的风险点已转移到 **阶段 4（`gow` 生成物治本）**——`skill.md` 的 10 项中**多数尚未修复**（§5.1 实测补充），且 `gow` 还需覆盖新出现的**手写装配链路**（`wiring.tpl`）与 **25 个 transport case**。

---

## 8. 验收标准

### 8.1 `kratos-bootstrap` / v3 谱系

```bash
# 每个子模块目录内
go build ./...
go test ./...
go vet ./...
```
- ✅ 全仓 `grep -r "go-kratos/kratos/v2" --include=*.go` 为 **0 命中**（`transport/*` 除外）
- ✅ 全仓 `grep -rn "log.Helper\|log.Fatalf\|log.NewStdLogger\|log.NewHelper\|log.DefaultTimestamp\|log.Valuer"` 为 **0 命中**
- ✅ `logger/v3` 包内 `grep -r "go-kratos"` 为 **0 命中**（零 kratos 依赖，只依赖 `log/slog` + otel trace）
- ✅ **无匿名导入也能工作**：`logger.NewLogger(nil)` / `NewLogger(&conf.Logger{Type:"std"})` 返回可用 logger（不是 nil）
- ✅ `log.SetDefault` 后，包级 `kratos/v3/log.Info("x","k","v")` 能落到配置的后端

### 8.2 `gow`

```bash
# 空目录
gow new demo
cd demo
gow add service user -s rest -s grpc -d ent -d redis
go build ./...
go test ./...
```
- ✅ 上述命令**一次通过，无手改**（这是 `skill.md` 存在的根本原因）
- ✅ 生成物中 `wire_gen.go` 存在且 `initApp` 可解析
- ✅ 生成物中不存在 `kratos/authn`、`pkg/middleware/auth`（除非显式 `--with-auth`）
- ✅ `cmd/server/assets/openapi.yaml` 存在
- ✅ `gow doctor` 全 PASS
- ✅ `gow add service x -s kafka` **报错退出**并说明原因（v3 谱系无该传输；**无 v2 回退**，不静默降级）
- ✅ `gow` 全仓 `grep -rn "kratos-version\|KratosVersion\|kratos/v2"` 为 **0 命中**（无兼容分支残留）

### 8.3 与 `bear-admin` 的一致性

- ✅ `bear-admin` 迁移后 `go build ./...` 通过，且**日志仍带 `trace_id`/`span_id`/`service.*`**
- ✅ `swagger-ui` 页面正常（零改动的验证点）
- ✅ HTTP/gRPC 服务能注册到 nacos 并正常摘除

---

## 9. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| `go-crud < v0.0.55` 把 kratos v2 拉回依赖图（**实测当前为 `v0.0.53`，仍不达标**） | 编译期出现 v2/v3 双份 kratos，或 logger 类型不匹配 | 模板与文档**强制 pin `>= v0.0.55`**；CI 加 `go mod graph \| grep kratos/v2` 断言 |
| `tx7do/kratos-transport` 长期不支持 v3 | 无法用 kafka/sse/mqtt/mcp | **已按方案 C 接受该缺口**（§4.6）：`bear-admin` 零使用，无回退；`gow` 中**显式失败**，不做静默降级；上游支持后按 §4.6.3 解锁 |
| `/v3` 谱系双份维护 | 每条 bug 修两次 | 只对 v3 做**功能演进**，v2 谱系**仅接受安全修复**，并设定 EOL 日期 |
| 本地 `replace` 漏项 → 静默从 proxy 拉 v2，`kratos/v2` 与 `kratos/v3` 同图 | 类型不匹配、难以定位 | `gow deps` / `gow doctor` 做「replace 覆盖率」检查；CI 断言 `go list -m all \| grep kratos/v2` 为空（§4.8.6 / §5.5） |
| 提交了 `go.work` 或用了绝对路径 `replace` | 同事 / CI 构建失败 | `go.work`/`go.work.sum` 必须 gitignore；`replace` 右侧只用相对路径；`gow doctor` 检查盘符（§4.8.6） |
| 日志格式变化导致既有日志采集/告警失配 | 运维侧告警规则失效 | 迁移前后各采一份样本对比；`slog` 的 key 命名与 v2 对齐（`ts`→保留 `time`，`trace_id`/`span_id` 不变） |
| `log.SetDefault` 全局单例 | 多实例/测试互相污染 | 只在 `NewLoggerProvider` 调用；各组件仍以**注入的 `*slog.Logger`** 为准；测试用 `slog.New(slog.NewTextHandler(io.Discard))` |
| `gow` 生成后自动 `go build` 引入网络/模块代理依赖 | CI 不稳定 | 校验步骤可 `--verify=false` 关闭；失败**回滚**生成文件 |
| `gow` 只产 v3，存量用户若仍需生成 v2 工程 | 新版 `gow` 无 v2 回退 | **已定，接受**（§5.0）：需要 v2 工程者使用旧版 `gow`；`kratos-bootstrap` v2 谱系仍可服务 `bear-admin` 等存量项目，但 `gow` 不再为它生成代码 |

**回滚**：v3 谱系是**新增模块路径**，v2 谱系完全不受影响 → 回滚 = 项目 `go.mod` 把 `*/v3` 换回原路径即可，无需 revert 代码。

---

## 10. 附录 A：本次实测证据（可复现）

```powershell
# 1) kratos v3 是否存在 / 版本
go list -m -versions github.com/go-kratos/kratos/v3
#   → github.com/go-kratos/kratos/v3 v3.0.0

# 2) v3 的 go 版本要求
#   kratos/v3@v3.0.0/go.mod → go 1.25.0

# 3) v3 核心 middleware 目录（tracing/metrics/auth 已移除）
#   v3@v3.0.0/middleware/ = circuitbreaker logging metadata ratelimit recovery selector validate

# 4) v3 已内置全部 codec，无需手工注册
#   v3@v3.0.0/transport/transport.go 内 _ import: encoding/{form,json,proto,protojson,xml,yaml}

# 5) v3 log 包 API（无 Logger/Helper/Valuer/Fatalf）
#   v3@v3.0.0/log/ = builder.go context.go filter.go handler.go level.go log.go
#   log.go 仅导出 Debug/Info/Warn/Error/Log/LogAttrs/With/WithGroup/SetDefault/Default
#   builder.go 导出 NewHandler/NewLogger/WithExtractor/WithWriter/WithFormat/WithLevel/WithFilter/WithAddSource
#   level.go: type Level = slog.Level; LevelFatal = slog.LevelError + 4

# 6) v3 的 HTTP binding 已并入 transport/http 包（无 binding 子包）
#   v3@v3.0.0/transport/http/path.go     → func BuildPath(pathTemplate string, msg any, opts ...BuildPathOption) string
#   v3@v3.0.0/transport/http/context.go  → (c *wrapper) Bind/BindVars/BindQuery/BindForm
#   v3@v3.0.0/transport/http/codec.go    → const SupportPackageIsVersion3 = true
#   v3@v3.0.0/errors/errors.go           → SupportPackageIsVersion1 = true（未变）

# 7) v3 ratelimit 不再需要 aegis
#   middleware/ratelimit/ratelimit.go → Server() 默认 limiter: internalratelimit.NewLimiter()
#   internal/ratelimit/ = ratelimit.go bbr.go

# 8) contrib 模块实测可用性
go list -m github.com/go-kratos/kratos/contrib/otel/v3@latest
#   → v3.0.0-20260626125723-668db92c2c00            ✅
go list -m github.com/go-kratos/kratos/contrib/middleware/jwt/v3@latest
#   → v3.0.0-20260626125723-668db92c2c00            ✅
go list -m github.com/go-kratos/kratos/contrib/registry/nacos/v3@latest
#   → v3.0.0-20260626125723-668db92c2c00            ✅
go list -m github.com/go-kratos/kratos/contrib/config/nacos/v3@latest
#   → v3.0.0-20260626125723-668db92c2c00            ✅
go list -m github.com/go-kratos/kratos/contrib/log/zap/v3@latest
#   → no matching versions                            ❌（只有 contrib/log/zap/v2）
go list -m github.com/go-kratos/kratos/contrib/encoding/json/v3@latest
#   → 不存在                                          ❌（v2ToV3.md 的说法有误）
go list -m github.com/go-kratos/kratos/contrib/middleware/tracing/v3@latest
#   → 不存在                                          ❌（tracing 在 contrib/otel/v3/tracing）

# 9) 第三方阻塞项（看 .mod 里的 kratos 依赖）
#   tx7do/kratos-transport/transport/sse@v1.3.8   → github.com/go-kratos/kratos/v2 v2.9.2  ❌
#   tx7do/kratos-transport/transport/kafka@v1.3.8 → github.com/go-kratos/kratos/v2 v2.9.2  ❌
#   tx7do/kratos-authn@v1.1.11                    → github.com/go-kratos/kratos/v2 v2.9.2  ❌
#   tx7do/go-crud/entgo@v0.0.51                   → github.com/go-kratos/kratos/v2 v2.9.2  ❌
#   tx7do/go-crud/entgo@v0.0.55                   → 无 kratos 依赖（改用 tx7do/go-wind）      ✅
#   tx7do/kratos-authz@v1.1.8                     → 无 kratos 依赖                            ✅
#   tx7do/kratos-swagger-ui@v0.0.0-20250528…      → 无 kratos 依赖（泛型 httpServerInterface）✅
#   tx7do/go-wind@v0.0.2                          → 无 kratos 依赖                            ✅

# 10) v3 HTTP Server 仍然兼容 swagger-ui
#   v3@v3.0.0/transport/http/server.go → (s *Server) HandlePrefix / Handle / HandleFunc

# 11) zapslog 归属独立模块（zap v1.27+）
go doc go.uber.org/zap/exp/zapslog
#   package zapslog // import "go.uber.org/zap/exp/zapslog"
#   func NewHandler(core zapcore.Core, opts ...HandlerOption) *Handler
#   func AddStacktraceAt(lvl slog.Level) HandlerOption
#   func WithCaller(enabled bool) HandlerOption
#   func WithCallerSkip(skip int) HandlerOption
#   func WithName(name string) HandlerOption
#   module go.uber.org/zap/exp v0.3.0（go.mod 声明 go 1.19，require zap v1.26.0）
```

---

## 11. 附录 B：待拍板事项

1. **模块谱系**（**仅关乎 `kratos-bootstrap`**）：确认走 `/v3` 后缀新谱系（推荐），还是单谱系在 `v0.x` 上破坏式升级？（§4.1）
   > 注：`gow` 侧已定「直接 v3、不做 v2 兼容」（§5.0），与本问**解耦**——无论本问如何选，`gow` 都只输出 v3。
2. ~~**`transport/*`**：确认本次冻结（推荐方案 C），还是投入 1d 做 fork 方案 B？~~ → **已定：方案 C，本次不迁移**，依据见 §4.6.1（`bear-admin` 零使用，无功能回退）。
3. **v2 谱系 EOL**：明确 v2 只收安全修复的截止日期。
4. **日志字段契约**：v3 下 `time`/`level`/`msg`/`source` 这套 slog 默认 key 是否需要改名以兼容既有采集（如 `ts`）？（§9）
5. **`gow` 的自动校验**：默认开启 `go build` 校验（更慢但更安全），还是默认关闭、CI 单独跑？
6. **`bear-admin`**：本次作为阶段 7 的验证样本一起迁，还是先只交付 `kratos-bootstrap/v3` + `gow` ？
7. **本地仓布局**：确认采用「`kratos-bootstrap` / `go-wind-toolkit` / `go-wind-admin-template` / `<项目>` **同级**」的约定（§4.8.3）？
   现状 `bear-admin` 的 `replace` 是 `../../../go/wind/kratos-bootstrap/...`（深层路径 + 另一个 checkout），
   建议本次一并收敛，否则 `gow` 自动生成的 `replace` 无法通用。
8. **`go.work` 是否纳入 CI**：默认不提交（各人布局不同）；若要 CI 也联调，则改为**提交方案 B 的 `replace`**（§4.8.4）。
9. **logger 契约（2026-09 新增）**：确认按 §4.2.0 **保持项目 `Logger` 接口不变**（推荐，只换 `slog` 桥），还是借本次 v3 迁移把契约统一收敛为 `*slog.Logger`？
10. **`go-wind` / `go-wind-plugins` 定位（2026-09 新增）**：二者作为 `database/ent` 的 indirect 依赖新出现，是否纳入 v3 谱系的正式依赖与版本策略？与 `go-crud` 的职责边界如何划分？
11. **`gow` 新命令面的 v3 化范围（2026-09 新增）**：`api`（buf）/`build`/`migrate`/`generate`/`extract` 的产物是否也需要 v3 化核对（§5.3 第 7 条）？还是本次只覆盖 `add service` + `new` 主链路？

---

## 12. 附：与既有文档的关系

| 文档 | 定位 | 本方案的处理 |
|---|---|---|
| `v2ToV3.md` | kratos 官方差异摘要 | **采纳**，但按 §1 修正 6 处 |
| `wind迁移v3.md` | 早前的迁移方案（含工时） | **继承**其 logger/`slog.Handler` 模板思路与分阶段结构；修正 §1、补依赖实测（§2）、补 `gow` 治本（§5） |
| `skill.md` | 「生成物有问题时的手工修复流程」 | **升级为生成器内置能力**（§5.1），目标是把这份 skill 变成**不必要** |
| `kratos-admin` | v3 参考实现 | **借鉴**其 `runtimeAccessFilter` 的 `slog.Handler` 写法（§4.2.0 的 `slog` 桥）；**反用**其架构（§3），避免重、耦合、不可自定义 |
| **本次实拉取（2026-09）** | `kratos-bootstrap` / `kratos-transport` / `go-wind-admin-template` / `go-wind-toolkit` 四仓真值 | **覆盖** §2 / §4.1 / §4.2 / §4.7 / §5.1–§5.5 / §6 中与实测冲突的「现状」描述；以顶部基线表为准 |
