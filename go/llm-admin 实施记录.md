# llm-admin 实施记录(实际执行步骤 + 踩坑)

> 这是「用 wind 从零实现 llm-manage 功能」的**实际执行日志**,供之后项目参考。
> 配套方案:`llm-admin 实例方案.md`;使用手册:`使用文档.md`。
> 路线:A(纯生成 + 增量手写)。项目:`E:/project/my-biz/my-go-admin/llm-admin`。

---

## 实际执行步骤(已验证的命令序列)

### 1. 建项目
```shell
cd E:/project/my-biz/my-go-admin        # 必须在此目录(wind new 自动发现本地模板)
wind new project llm-admin              # 注意:是 new project 子命令,new 壳本身无 --module flag
cd llm-admin
```
- ⚠️ **坑 1**:`--module` flag 未生效(已知问题),module 会是目录名。改名方法:
  ```shell
  # 全局替换 module(会同时改坏 pb.go 描述符字面量)
  grep -rl "llm-admin" --include="*.go" --include="*.mod" --include="*.yaml" . | \
    while read f; do sed -i "s|module llm-admin$|module github.com/xianyu/llm-admin|; s|llm-admin/api|github.com/xianyu/llm-admin/api|g; s|\"llm-admin/|\"github.com/xianyu/llm-admin/|g" "$f"; done
  # ★ 改名后必须重新生成,否则 pb 描述符长度错位 → 运行时 init panic
  wind api && go build ./...
  ```

### 2. 配置
- `app/admin/service/configs/bootstrap.yaml`:nacos `10.0.0.5:8848` / ns `xhf` / nacos/nacos / dataId `llm-admin`;OTLP `10.0.0.5:4317`
- `configs/data.yaml`:driver postgres + pg DSN(host=10.0.0.5 ... dbname=good_fish),**migrate: false**(共享库)

### 3. 加服务 + CRUD 生成
```shell
# 模板自带 app/admin 示例时,wind add service admin 会报已存在——直接在示例服务上生成即可
wind generate --dsn file://llm.sql --driver mysql --service admin --orm gorm -s grpc,rest -t llm_provider -t llm_model
```
- ⚠️ **坑 2**:gorm 导入器的内存解析是 **MySQL 方言**,不认 TIMESTAMPTZ/BIGSERIAL → 生成用 DDL 写 MySQL 方言(运行时 data.yaml 仍是 postgres,互不影响)
- ⚠️ **坑 3**:`-s` 不传默认只有 grpc!rest 的注入/路由要显式 `-s grpc,rest`
- ⚠️ **坑 4**:首跑 go mod tidy 解析 `tx7do/go-crud/gorm` 会撞代理 @latest 陷阱(解析到 2021 年父模块)→ 已由 verify 链自动按 pin 版本 `go get`(deps.go)

### 4. REST 路由注解(生成器产出是 grpc-only,REST 注解手工补)
生成的 proto 无 google.api.http 注解。照参考项目路径手工补(见 llm-admin 两个 proto 的写法),然后:
```shell
wind api && go build ./...    # ✅ 全绿
```

### 5. 启动验证
```shell
cd app/admin/service && go build -o ../../server.exe ./cmd/server
../../server.exe -conf ./configs
```
- nacos 控制台(ns xhf)出现 `llm-admin/admin-service.http/.grpc` 实例
- dataId `llm-admin` 远端配置可覆盖本地端口(热更新 Watch 已实现)
- `wind doctor --net` 全绿

---

## 踩坑总表(按发现顺序)

| # | 现象 | 根因 | 解法/状态 |
|---|---|---|---|
| 1 | `wind new -m` 不认识 | new 壳无 flag,需 `wind new project` | 已知,建议后续把 flag 合并到 new 壳 |
| 2 | 模块名 sed 后运行时 init panic | 模块替换改坏 pb.go 描述符字面量长度 | 改名后必须 `wind api` 重新生成 |
| 3 | TIMESTAMPTZ 解析失败 | gorm 导入器 rawsql 仅 MySQL 方言 | 生成用 DDL 写 MySQL 方言 |
| 4 | tidy 撞 go-crud@v0.0.6 代理陷阱 | @latest 解析到 2021 父模块 | verify 链按 deps.go pin 自动 go get |
| 5 | 生成 import 前缀是 `llm-admin/` | gow 原版取 module 末段当 projectName | 已修:用完整 module 路径 |
| 6 | "回滚"没回滚 | 快照拍在生成之后 | 已修:快照移到生成前 |
| 7 | wiring 出现双份错误 import | 残留文件 + 注入叠加 | 从模板恢复原始态重新生成 |
| 8 | rest 注入从未发生 | **漏传 `-s grpc,rest`** | 注意:generate 默认 grpc |
| 9 | `undefined: NewRestServer` 等 | 手写形态示例服务与注入约定不兼容 | 模板示例已改名 `sample`,让出 admin |
| 10 | 锚点判重误杀多 server 注入 | SkipIf 文件级判重 | 已修:锚点后分段判重 |
| 11 | rest 路由 import 指到 admin/service/v1 | 域名推导把仓储型 rest 当 BFF | 已修:仓储型 rest 与 grpc 同域 |
| 12 | `_http.pb.go` 缺失 | 生成 proto 无 google.api.http 注解(gow 原版即如此) | 手工补注解(照参考路由) |

## 待办(功能对齐参考项目的剩余项)

- [ ] Model.Enable 互斥启用(repo 事务:全停→启用)+ GetEnabled
- [ ] provider_name 关联查询(Model List/Get 返回)
- [ ] api_key 出参脱敏
- [ ] Provider.TestConnection 探活
- [ ] 内部 gRPC 域 llm.proto(GetEnabledModel / ReportUsage)
- [ ] List 过滤(provider_id / display_name 模糊 / status)
- [ ] 雪花 ID(后置演进,参考项目迁移脚本可照抄)

## 追记(2026-11-11):配置被冲掉事件与配置分层改造

**现象**:模板恢复 app/admin/service(修注入兼容)时,把已配置好的真实 nacos 地址/账号密码与 pg DSN 一起冲回占位符 → 用户改配置时发现"连 nacos 都连不上,账号密码没配置";且 nacos 3.x + 鉴权开启 + 空凭据 = 客户端**静默**连不上(无明确报错)。

**改造:配置三层分层**
| 层 | 位置 | 用途 | 入库 |
|---|---|---|---|
| L1 默认 | configs/bootstrap.yaml | 结构骨架+占位 | ✅ |
| L2 本机覆盖 | configs/local.yaml | 真实地址/账号密码(排序在 bootstrap.yaml 之后自动覆盖) | ❌ gitignore |
| L3 环境 | WIND_NACOS_USERNAME 等 | CI/部署(最高优先级) | - |

**坑 13**:nacos 3.x + auth + 空凭据 = 静默连不上;data.yaml 与 bootstrap.yaml 同属模板恢复覆盖范围,真实值一律放 local.yaml 或 nacos 远端。

**遗留阻塞**:pg 10.0.0.5:5432 从开发机不通(8848 同主机可达 → 5432 被防火墙拦截或 pg 未监听)——待运维放开后,服务即可完整启动并注册。

## 追记(2026-11-11):REST 字段丢失与 codec 改造

**现象**:CRUD Create 成功但 base_url/api_key 落库为空;响应为 snake_case。

**根因**:kratos v3 把 REST `application/json` 的编解码默认切到了标准库 json(按 Go 字段名/snake_case json tag 匹配),前端发 camelCase(`baseUrl`)绑不上 → 静默丢字段。v2 时代该行为是 protojson(v2ToV3 迁移文档「显式选择 JSON codec」的实际影响)。

**改造**:模板 `pkg/bootstrap/codec.go` 注册 protojson 语义、名为 `json` 的 codec(全项目 REST 恢复 v2 行为:请求接受 json_name/camelCase,响应输出 camelCase,int64 输出为字符串——前端注意)。

**坑 14**:kratos `config.Load` 会对每个 source 自动调 `Watch()`,返回 error 会让整个加载失败——自实现 source 必须提供可用的 Watcher(可永久阻塞)。
**坑 15**:kratos 官方 env source 不做大小写/层级映射(`WIND_SERVER_HTTP_ADDR` 变成顶层键)→ 自实现 `newEnvSource`(段小写、点分层级)。注意驼峰键(dataId/timeoutMs)无法经环境变量表达,只能 local.yaml/远端配置。
**坑 16**:CRUD 生成的 Get/Delete 请求 id 是 `uint32`(gow 的 mysql 类型映射),雪花 int64 溢出 → proto 里手改 `int64 id`(参考项目本就是 int64)。

**llm-admin 实测**:Create(雪花 id=2120537918429855744,baseUrl/apiKey 正确入库回显)→ List(protojson 输出,total=8)→ 全链路绿。注意 id 在 protojson 输出里是字符串,前端按字符串处理。

## 追记(2026-11-12):7 项功能对齐全部完成

| # | 功能 | 验证方式 | 结果 |
|---|---|---|---|
| 1 | Enable 互斥启用 | PUT /llm/model/enable/{id} → status 置 1,事务先全停 | ✅ |
| 2 | provider_name 关联 | List/Get/Enable 返回均带 providerName=siliconflow | ✅ |
| 3 | api_key 脱敏 | Get/List 返回 sk-******3456;Create 响应同样脱敏 | ✅ |
| 4 | TestConnection | 按 id / 按传入配置两种方式,探活 {base_url}/v1/models + 延迟 | ✅(外网不可达时正确返回失败信息) |
| 5 | 内部 gRPC 域 | llm.proto(LlmService)→ gRPC 直连 9000 实测 GetEnabledModel 返回完整配置(含明文 key) | ✅ |
| 6 | List 过滤 | providerId+status 组合过滤 / display_name 模糊匹配 | ✅ |
| 7 | 雪花迁移脚本 | sql/llm_manage_id_to_snowflake.sql(真实表已是雪花主键,脚本供新环境参考) | ✅ |

**追加踩坑(模板层,已在 wind-template 提交)**:
- **坑 14**:kratos `config.Load` 会对每个 source 调 `Watch()`,source 的 Watch 返回 error 会让整个配置加载失败——自实现 source 必须提供可用 Watcher(可永久阻塞)。
- **坑 15**:kratos 官方 env source 不做层级映射(`WIND_SERVER_HTTP_ADDR` 停在顶层键)→ bootstrap 自实现 `newEnvSource`(WIND_段_段 → 段.段)。注意驼峰键(dataId)无法经环境变量表达。
- **坑 16**:kratos v3 REST 默认编解码是**标准库 json**(按 Go 字段名/snake_case tag 匹配)→ 前端 camelCase 字段静默丢失。bootstrap 注册 protojson 语义、名为 `json` 的 codec(恢复 v2 行为;响应 protojson 风格,**int64 输出为字符串**,前端按字符串处理)。

**本轮模板变更清单(wind-template 提交记录)**:
1. `pkg/bootstrap/codec.go` — protojson json codec
2. `pkg/bootstrap/envsource.go` + config.go 调整 — 层级 env 源 + 预读轻量化(消除 watcher 噪音)
3. 示例服务 admin → sample(与生成器注入约定兼容,让出 admin 名)
4. `.gitignore` 加 `local.yaml`(配置分层本机覆盖层)
5. buf.yaml/buf.gen.yaml — 补 gnostic/kratos apis/pagination 依赖 + managed/disable + M 映射
