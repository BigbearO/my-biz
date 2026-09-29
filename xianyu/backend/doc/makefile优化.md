# Makefile 优化对照

| 项 | 内容 |
|---|---|
| 优化版（基准） | `backend/Makefile` |
| 对比基线 | `backend/llm-manage/Makefile`（gow 生成版） |
| 更新日期 | 2026-09-29 |

---

## 1. 差异总览

| 变更项 | 基线（llm-manage/Makefile） | 优化后（backend/Makefile） |
|---|---|---|
| `.PHONY` | 12 个目标 | 新增 `register`、`install-dev`、`install-prod`、`docker-up`、`docker-down`、`docker-libs`、`pm2-deploy` |
| 命令回显 | 全部 `@` 静默 | 去掉 `@`，回显执行的命令 |
| `plugin` | `menta2k/protoc-gen-redact/v3`、`go-kratos/protoc-gen-typescript-http`、`npm install -g ts-proto` | 换源 `tx7do/go-wind-toolkit/protoc-gen-go-redact`、`tx7do/go-wind-toolkit/protoc-gen-typescript-http`；去掉 `ts-proto` npm 安装 |
| `cli` | `golangci-lint/cmd/golangci-lint` | 升级为 `golangci-lint/v2/cmd/golangci-lint` |
| `register` | 无 | 新增：向手写 wiring 注入 CRUD 模块 |
| `ts` | 单模板 `buf.admin.typescript.gen.yaml` | 三个前端模板：vue-vben / vue-element / react |
| 部署脚本 | 无 | 新增 Script Commands 区块：`install-dev` / `install-prod` / `docker-up` / `docker-libs` / `docker-down` / `pm2-deploy`，`docker-up`/`docker-libs` 按 `IS_WINDOWS` 分支走 PowerShell 或 bash |

未变动：`init: plugin cli`、`dep/vendor/test/cover/vet/lint`、`ent/gen/api/openapi`、`build/build_only/all`、`export`、`compose-*`、`docker`、`help`。

---

## 2. `plugin` / `cli`（优化后）

```make
# initialize develop environment
init: plugin cli

# install protoc plugin
plugin:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
	go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
	go install github.com/go-kratos/kratos/cmd/protoc-gen-go-http/v2@latest
	go install github.com/go-kratos/kratos/cmd/protoc-gen-go-errors/v2@latest
	go install github.com/google/gnostic/cmd/protoc-gen-openapi@latest
	go install github.com/envoyproxy/protoc-gen-validate@latest
	go install github.com/tx7do/go-wind-toolkit/protoc-gen-go-redact@latest
	go install github.com/tx7do/go-wind-toolkit/protoc-gen-typescript-http@latest

# install cli tools
cli:
	go install github.com/go-kratos/kratos/cmd/kratos/v2@latest
	go install github.com/google/gnostic@latest
	go install github.com/bufbuild/buf/cmd/buf@latest
	go install entgo.io/ent/cmd/ent@latest
	go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest
	go install github.com/tx7do/go-wind-toolkit/gowind/cmd/gow@latest
```

与基线的差别：

| 项 | 基线 | 优化后 |
|---|---|---|
| redact 插件 | `github.com/menta2k/protoc-gen-redact/v3` | `github.com/tx7do/go-wind-toolkit/protoc-gen-go-redact` |
| typescript-http 插件 | `github.com/go-kratos/protoc-gen-typescript-http` | `github.com/tx7do/go-wind-toolkit/protoc-gen-typescript-http` |
| `ts-proto` | `@npm install -g ts-proto` | 移除（依赖 Node/npm） |
| golangci-lint | `golangci-lint/cmd/golangci-lint` | `golangci-lint/v2/cmd/golangci-lint` |

---

## 3. 新增目标

### 3.1 `register` — wiring 注入

```make
# register a new CRUD module into the hand-written wiring (usage: make register ENTITY=product)
register:
	go run ./tools/register -entity $(ENTITY)
```

用法：

```shell
make register ENTITY=product
```

对应 `app/admin/service/cmd/server/wiring.go` 的 `register:repo` / `register:service` / `register:rest-arg` / `register:grpc-arg` 锚点，以及 `internal/server/{rest,grpc}_server.go` 的 `register:param` / `register:route`。

### 3.2 Script Commands

```make
install-dev:
	bash scripts/env/install_unix_dev.sh

install-prod:
	bash scripts/env/install_unix_prod.sh

docker-up:
ifdef IS_WINDOWS
	powershell -ExecutionPolicy Bypass -File scripts/docker/full_deploy.ps1
else
	bash scripts/docker/full_deploy.sh
endif

docker-libs:
ifdef IS_WINDOWS
	powershell -ExecutionPolicy Bypass -File scripts/docker/libs_only.ps1
else
	bash scripts/docker/libs_only.sh
endif

docker-down:
	docker compose down

pm2-deploy:
	bash scripts/deploy/pm2_service.sh
```

| 目标 | 作用 |
|---|---|
| `install-dev` / `install-prod` | 安装开发 / 生产环境（Unix/Linux/macOS） |
| `docker-up` | 启动全部服务（应用 + 依赖），Windows 走 PowerShell |
| `docker-libs` | 只启动依赖服务（不含应用），Windows 走 PowerShell |
| `docker-down` | 停止全部 docker compose 服务 |
| `pm2-deploy` | PM2 部署服务 |

### 3.3 `ts` — 多前端模板

```make
ts:
	cd api && \
	buf generate --template buf.vue-vben.admin.typescript.gen.yaml && \
	buf generate --template buf.vue-element.admin.typescript.gen.yaml && \
	buf generate --template buf.react.admin.typescript.gen.yaml
```

---

## 4. 优化后全量目标清单

| 分组 | 目标 |
|---|---|
| 环境 | `init`（= `plugin` + `cli`）、`plugin`、`cli`、`env`、`dep`、`vendor` |
| 生成 | `ent`、`gen`（= ent + api + openapi）、`api`、`openapi`、`ts`、`register` |
| 构建 | `build`、`build_only`、`all` |
| 检查 | `test`、`cover`、`vet`、`lint` |
| 配置 | `export` |
| Docker | `compose-up`、`compose-restart`、`compose-down`、`compose-up-without-service`、`docker`、`docker-up`、`docker-libs`、`docker-down` |
| 部署/环境 | `install-dev`、`install-prod`、`pm2-deploy` |
| 帮助 | `help`（默认目标） |

---

## 5. 待办（新增目标引用的文件尚不存在）

| 引用 | 现状 |
|---|---|
| `./tools/register`（`register` 目标） | `backend/tools/register` 未创建 |
| `scripts/env/install_unix_dev.sh`、`install_unix_prod.sh` | `backend/scripts/` 未创建 |
| `scripts/docker/full_deploy.{sh,ps1}`、`libs_only.{sh,ps1}` | 同上 |
| `scripts/deploy/pm2_service.sh` | 同上 |
| `buf.vue-vben/vue-element/react.admin.typescript.gen.yaml` | `llm-manage/api/` 目前只有 `buf.admin.typescript.gen.yaml` |
| `cfgexp`（`export` 目标） | `cli` 未安装，需要时 `go install github.com/tx7do/kratos-cli/config-exporter/cmd/cfgexp@latest` |
