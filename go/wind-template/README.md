# wind-template

自研 kratos v3 微服务脚手架的模板仓(被 `wind new` / 官方 `kratos new -r` 使用)。

## 特性
- kratos **v3** + 官方组件:nacos 注册发现(`contrib/registry/nacos/v3`)、nacos 配置中心(`contrib/config/nacos/v3`)、OTEL 链路(`contrib/otel/v3/tracing`)、**日志走 OTLP 转发**(`contrib/otel/v3/log`)
- 无鉴权:鉴权由网关完成,中间件仅 recovery + logging + validate + tracing
- monorepo 多服务:`app/<name>/service/{cmd/server, internal, configs}`,手写 `wiring.go` + 锚点注入
- proto 多语言共用:默认 `api/protos`,可指公共目录(见 `.scaffold.example.yaml`)

## 快速开始

```bash
make init        # 安装 protoc 插件与 buf
make api         # proto → go(产物 api/gen/go)
make build       # 编译全部服务
# 运行
cd app/sample/service && go run ./cmd/server -conf ./configs
# 验证
curl http://127.0.0.1:8000/helloworld/v1/greeter/wind
```

## 新增服务
`wind add service <name> -s grpc -d ent`(脚手架 CLI 生成三层骨架与锚点),或手工复制 `app/sample/service` 改名。

## 配置契约
| 文件 | 内容 | 覆盖顺序(低→高) |
|---|---|---|
| `configs/bootstrap.yaml` | app / nacos / trace / log(始终本地) | 本地 < nacos(业务段) < `WIND_*` 环境变量 |
| `configs/server.yaml` | grpc/http 监听地址 | 同上 |
| `configs/data.yaml` | 数据源(由 nacos 下发为佳) | 同上 |

## 锚点
`wiring.go` 与 `internal/server/*` 内含 `register:*` 锚点注释(与 gow 生态逐字节一致),`wind generate` 只做锚点注入,不覆盖手写内容。

## 环境要求

- Go 工具链 ≥ 1.26.4(go-crud 要求;ent codegen 需在 ≤1.26 工具链下执行,`wind ent` 已自动处理)
- protoc 插件:protoc-gen-go / go-grpc / **go-http(v3)** / go-errors(v3) / openapi —— `make init` 一键安装
- kratos CLI v3:`wind doctor` 会校验版本
