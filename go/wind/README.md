# wind

基于 **kratos v3 官方组件**的自研微服务脚手架 CLI(fork 自 gowind,面向 kratos v3 全面重写)。

- 生成即可编译:模板/生成链以「生成物不改一行即通过 `go build`」为红线,生成后自动 gofmt → buf → build 校验,失败回滚
- 以官方组件为底:nacos 注册发现(`contrib/registry/nacos/v3`)、nacos 配置中心(`contrib/config/nacos/v3`)、OTEL 链路+日志 OTLP 转发(`contrib/otel/v3`)
- 不做鉴权:鉴权由网关完成,中间件仅 recovery + logging + validate + tracing
- proto 多语言共用:默认 `api/protos`(可指公共目录),`wind api --path` 指定 proto 文件/目录增量生成
- 双 ORM:ent / gorm;装配为手写 `wiring.go` + 锚点注入(与手写代码共存)

## 安装

```shell
go install ./cmd/wind   # 或发布后 go install github.com/wind-scaffold/wind/cmd/wind@latest
wind version
wind doctor             # 环境体检(工具链/插件版本/项目分层铁律;--net 探测 nacos/OTLP)
```

## 快速开始

```shell
# 1. 新建项目(开发期默认用本地 kratos-template 模板;否则走 GitHub+gh-proxy 拉取)
wind new demo
cd demo

# 2. 新增服务(可选 ent/gorm,grpc/rest)
wind add service user -s grpc,rest -d ent

# 3. proto(默认模式转官方约定;外置公共目录见 .scaffold.yaml 的 proto_dir)
wind proto add user/service/v1/user.proto

# 4. CRUD:从 DDL/ent schema 生成 proto→ORM→repo→service→锚点装配(生成后自动校验编译)
wind generate --dsn file://user.sql --service user --orm ent -t user

# 5. ent runtime + buf 生成 + 编译
wind ent user
wind api            # 全量;--path <file|dir> 增量;--lang go,openapi 过滤
go build ./...
```

## 命令

| 命令 | 说明 |
|---|---|
| `wind new <name>` | 新建项目(模板来源:默认 / `-r <git url>` / `--template <本地目录>`) |
| `wind add service <name>` | 服务骨架(`-s grpc,rest` `-d ent,gorm`) |
| `wind proto add <path>` | 新增 proto 模板(两种目录模式产物逐字节一致) |
| `wind api` | proto → 代码(buf;`--path`/`--lang`) |
| `wind ent` | ent schema 管理(generate/add) |
| `wind generate` | CRUD 全链生成(`--dsn file://x.sql` / `ent://<schema目录>`;`--verify=false` 跳过校验) |
| `wind doctor` | 环境与项目体检 |

## 约定与边界

- kratos v3 谱系仅支持 grpc/rest 传输(kafka/sse/mqtt 等的 kratos-transport 未升 v3,显式报错)
- go-crud(entgo/gorm)只允许在 `internal/data` 层引用,doctor 强制检查
- 生成项目依赖版本集中在 `pkg/generators/deps.go`(nacos config pin、go-crud pin、Go 1.26.4)

## 已知环境坑

- entgo 内嵌 x/tools 读不懂 go 1.27 的 export data:`wind ent` 已强制 `GOTOOLCHAIN=go1.26.4`
- 代理对部分模块 `@latest` 解析不稳:kratos v3 CLI 用 gh-proxy 克隆源码安装更稳
  (`git clone https://v6.gh-proxy.org/https://github.com/go-kratos/kratos`,在 `cmd/kratos` 下 `go install .`)
