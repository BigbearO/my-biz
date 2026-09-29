# gow（GoWind CLI）使用 & Makefile 优化

| 项 | 内容 |
|---|---|
| 上游文档 | <https://github.com/tx7do/go-wind-toolkit/blob/main/gowind/README.md> |
| 适用模块 | `backend/llm-manage`（module `llm-manage`，当前仅 `app/admin/service` 一个服务） |
| 相关 Makefile | `backend/Makefile` = `backend/llm-manage/Makefile`；服务级 Makefile 只有一行 `include ../../../app.mk` |
| 更新日期 | 2026-09-29 |

---

## 1. 安装

```shell
go install github.com/tx7do/go-wind-toolkit/gowind/cmd/gow@latest
gow version
gow help
```

本项目用 `make init`（= `plugin` + `cli`）。

已废弃：`go install github.com/tx7do/kratos-cli/gowind/cmd/gowind@latest`。`llm-manage/README.md` 里的旧写法需更新为 `gow`。

---

## 2. 标准流程

```
gow new  →  go mod tidy  →  gow add service  →  go mod tidy
    →  gow generate（或 gow extract）  →  gow ent / gow wire / gow api
    →  gow run / gow build
```

### 2.1 创建项目

```shell
gow new myproject
cd myproject && go mod tidy

gow new myproject -m github.com/yourusername/myproject
```

默认生成 `.github/workflows/ci.yml`（已存在则不覆盖），`--no-ci` 跳过。

### 2.2 添加微服务

```shell
gow add service admin
gow add service order -s grpc
gow add service admin -s rest -s grpc            # gRPC + REST
gow add svc payment -d gorm -s grpc              # ORM + 协议
gow add service admin -s rest -s grpc -d gorm -d redis
```

`-s websocket`：消息驱动，不注册 proto 服务；需在 `configs/*.yaml` 补 `server.websocket`（`network`/`addr`/`path`/`codec`），消息处理器登记在 `register:route` 锚点后。

### 2.3 数据库 / 源码 schema 生成 CRUD

```shell
gow generate                                     # 交互式
gow generate --dsn "mysql://user:pass@tcp(localhost:3306)/dbname" --service user
gow generate --dsn "mysql://..." --service user --orm ent --servers grpc --tables users,roles
gow generate --dsn "postgres://..." --service admin --proto-only
gow generate --dsn "mysql://..." --service user-admin --servers rest --source-module user --skip-orm
gow gen --dsn "..." --service user               # 别名

# 源码 schema（不连数据库）
gow generate --dsn "ent://internal/data/ent/schema" --service user --orm ent
gow generate --dsn "gorm://internal/data/models" --service payment --orm gorm
gow generate --dsn "ent://..." --service user -n   # dry-run
```

约束：`ent://` 必配 `--orm ent`，`gorm://` 必配 `--orm gorm`；ent 源的 Mixin/GoType/SchemaType 覆写无法还原（打印 `[WARN]`）；m2m 中间表从 proto 剔除；gorm 源对 models 目录只补缺失文件，冲突即跳过、不覆盖。

### 2.4 模块提取

```shell
gow extract admin user --obj role
gow extract admin user -o role,permission
gow extract admin user -o role --keep-source
gow extract admin user -o role --orm gorm
```

### 2.5 工具代码生成

```shell
gow ent           # gow ent admin
gow api
gow wire          # gow wire admin；手写装配服务自动跳过
```

### 2.6 运行与编译

```shell
gow run           # 在 app/xxx/service 下；否则运行模块内全部服务
gow run admin

gow build
gow build admin
gow build --os linux,windows --arch amd64 --out ./dist   # 产物带 _<goos>_<goarch> 后缀
```

---

## 3. 命令速查

| 命令 | 用法 | 主要 flag |
|---|---|---|
| `gow new` | `gow new <name> [-m module]` | `-m/--module`、`--no-ci` |
| `gow add service` | `gow add service <name>` | `-s/--server`（`grpc`/`rest`/`websocket`）、`-d/--dao`（`gorm`/`ent`/`redis`）、`-o/--orm`（默认 `ent`）、`--wire` |
| `gow generate` / `gow gen` | `gow generate [flags]` | `--dsn`、`--driver`（默认 `mysql`）、`--service`、`--orm`、`-s/--servers`（默认 `[grpc]`）、`-t/--tables`、`--exclude-tables`、`--module-version`（默认 `v1`）、`--proto-only`、`--skip-orm`、`--skip-config`、`--skip-makefile`、`--source-module`、`-n/--dry-run` |
| `gow extract` | `gow extract <src> <dst> -o <model>` | `-o/--obj`、`--orm`、`--keep-source` |
| `gow run` | `gow run [service]` | — |
| `gow build` | `gow build [service]` | `--os`、`--arch`、`--out` |
| `gow ent` / `gow wire` | `gow ent [service]` | — |
| `gow api` | `gow api` | — |
| `gow version` / `gow help` | — | — |

易混点：

- `-o`：`gow add service -o` 是 ORM 类型；`gow extract -o` 是实体名。
- `-s`：都是服务/传输类型（`grpc`/`rest`/`websocket`）。
- 源码 schema 源必须与 `--orm` 匹配。
- 生成前先 `-n` 干跑。

---

## 4. Makefile：`init` = `plugin` + `cli`

```make
init: plugin cli

plugin:   # protoc 生成器插件
	@go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
	@go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
	@go install github.com/go-kratos/kratos/cmd/protoc-gen-go-http/v2@latest
	@go install github.com/go-kratos/kratos/cmd/protoc-gen-go-errors/v2@latest
	@go install github.com/google/gnostic/cmd/protoc-gen-openapi@latest
	@go install github.com/envoyproxy/protoc-gen-validate@latest
	@go install github.com/menta2k/protoc-gen-redact/v3@latest
	@go install github.com/go-kratos/protoc-gen-typescript-http@latest
	@npm install -g ts-proto

cli:      # 命令行工具
	@go install github.com/go-kratos/kratos/cmd/kratos/v2@latest
	@go install github.com/google/gnostic@latest
	@go install github.com/bufbuild/buf/cmd/buf@latest
	@go install entgo.io/ent/cmd/ent@latest
	@go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	@go install github.com/tx7do/go-wind-toolkit/gowind/cmd/gow@latest
```

与旧模板（`bear-admin/Makefile`）的差异：

| 项 | 本项目 |
|---|---|
| `plugin` | 去掉 Dart（`flutter pub global activate protoc_plugin`），保留 go 插件 + `ts-proto` |
| `cli` | 去掉 `cfgexp` / `sql2orm` / `sql2proto` / `sql2kratos`；`gow` 改用 `tx7do/go-wind-toolkit` |
| `wire` | 移除 wire 目标，`gen: ent api openapi` |
| `init` | 拆为 `plugin` / `cli` 两个可独立执行的目标 |

待办：`export` 依赖 `cfgexp`，`cli` 已不安装。需要时：

```shell
go install github.com/tx7do/kratos-cli/config-exporter/cmd/cfgexp@latest
```

顶层目标速查：

| 目标 | 作用 |
|---|---|
| `init` | = `plugin` + `cli` |
| `dep` / `vendor` | `go mod download` / `go mod vendor` |
| `ent` | 遍历 `app/*/*/Makefile` 执行各服务 `make ent` |
| `api` / `openapi` / `ts` | `cd api && buf generate`（go / openapi / typescript） |
| `gen` | = `ent` + `api` + `openapi` |
| `build` | `api` + `openapi` 后遍历各服务 `make build` |
| `build_only` | 只遍历各服务 `make build` |
| `all` | 遍历各服务 `make app` |
| `test` / `cover` / `vet` / `lint` | 测试 / 覆盖率 / 静态检查 / golangci-lint |
| `compose-up` / `compose-restart` / `compose-down` | docker compose |
| `docker` | 遍历各服务 `make docker` |
| `help` | 默认目标 |

注意：

1. `api`/`openapi` 只在顶层 Makefile 有 recipe，服务目录里 `make build`/`make run` 不会触发 buf 生成；生成走顶层 `make api` / `make gen` / `make build`。
2. `app.mk` 的 `ent` 有守卫，`internal/data/ent` 不存在时为空操作：

   ```make
   ent:
   ifneq ("$(wildcard ./internal/data/ent)","")
   	@ent generate --feature privacy --feature entql --feature sql/modifier \
   		--feature sql/upsert --feature sql/lock ./internal/data/ent/schema
   endif
   ```

---

## 5. 装配约定：手写 `wiring.go` + `register:` 锚点

注入点：`llm-manage/app/admin/service/cmd/server/wiring.go`。

```go
func initApp(ctx *bootstrap.Context) (*kratos.App, func(), error) {
	var cleanups []func()
	rollback := func() {                 // 逆序（LIFO）回滚
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

	greeterRepo := data.NewGreeterRepo(ctx)
	// ── register:repo ── 新模块仓储在此行后注册(勿删)

	greeterService := service.NewGreeterService(ctx, greeterRepo)
	// ── register:service ── 新模块服务在此行后注册(勿删)

	restMiddlewares := server.NewRestMiddleware(ctx)
	httpServer := server.NewRestServer(ctx, restMiddlewares,
		greeterService,
		// register:rest-arg ── 新模块服务实参在此行后追加(勿删)
	)
	grpcMiddlewares := server.NewGrpcMiddleware(ctx)
	grpcServer := server.NewGrpcServer(ctx, grpcMiddlewares,
		greeterService,
		// register:grpc-arg ── 新模块服务实参在此行后追加(勿删)
	)

	return newApp(ctx, httpServer, grpcServer), rollback, nil
}
```

规则：

1. 装配顺序：基础设施 → 仓储(data) → 服务(service) → 传输(server)，单向。
2. 新模块只在锚点后追加构造行，锚点注释不删。
3. 持有 cleanup 的资源创建后立即注册进 `cleanups`。
4. 该文件只做构造与传参。
5. 锚点：`wiring.go` 的 `register:repo` / `register:service` / `register:rest-arg` / `register:grpc-arg`；`internal/server/rest_server.go`、`grpc_server.go` 的 `register:param` / `register:route`。
6. 旧式 wire 由 `gow add service --wire` 生成；`gow wire` 对手写装配服务自动跳过。

---

## 6. 路径对照

| 项 | 位置 |
|---|---|
| 项目根 | `backend/llm-manage` |
| 服务目录（`gow run` 执行目录） | `llm-manage/app/admin/service` |
| 入口 | `app/admin/service/cmd/server/main.go` |
| 装配点 | `app/admin/service/cmd/server/wiring.go` |
| proto 源 | `api/protos/admin/service/v1`、`api/protos/helloworld/service/v1` |
| buf 配置 | `api/buf.yaml`、`api/buf.gen.yaml`、`api/buf.admin.openapi.gen.yaml`、`api/buf.admin.typescript.gen.yaml` |
| 生成产物 | `api/gen/go/...`；OpenAPI 在 `app/admin/service/cmd/server/assets` |
| 服务配置 | `app/admin/service/configs/*.yaml` |
| CI | `.github/workflows/ci.yml` |

日常命令：

```shell
cd backend/llm-manage
make init        # 首次
make gen         # ent + api + openapi
make build       # 生成 + 遍历各服务编译
make all         # 生成 + 构建

cd app/admin/service && make run    # 本地跑服务
gow run admin                       # 需在 app/admin/service 下
```

---

## 7. FAQ

| 现象 | 处理 |
|---|---|
| `gow wire` 无输出 | 手写装配服务被自动跳过，属预期 |
| `ent://`/`gorm://` 源报错 | 必须与 `--orm` 匹配 |
| `-o` 行为不一致 | `add service` 是 ORM 类型，`extract` 是实体名 |
| 服务目录 `make build` 后 proto 未更新 | 改从顶层 `make build` / `make api` 驱动 |
| `make ent` 无动作 | `internal/data/ent` 不存在，守卫生效 |
| websocket 未生效 | 补 `configs/*.yaml` 的 `server.websocket`，并在 `register:route` 后登记处理器 |
| `make export` 找不到 `cfgexp` | 见第 4 节待办 |
| 怕覆盖手写代码 | 先 `-n` 干跑；gorm 源只补缺失文件、冲突跳过 |

---

## 8. 版本差异

| 能力 | main 分支文档 | 本机快照 `go/go-wind-toolkit` |
|---|---|---|
| `gow new` | 生成脚手架，`-m`、`--no-ci` | 克隆 `go-wind-admin-template`，flag `-r/-b/-t/-m/--nomod`，创建后自动 tidy + buf generate |
| `gow build` 交叉编译 | 有 | 无 |
| websocket 传输 | 有 | 无 |
| `wiring.go` + `register:` 锚点 | 有 | 无 |

实际安装的 `gow@latest` 以 `gow help` 输出为准；本地快照仅作参考。
