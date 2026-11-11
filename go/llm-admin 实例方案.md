# 实例方案:用 wind 脚手架重写 llm-manage(大模型管理)

> 参考:`E:/project/my-biz/xianyu/backend/llm-manage`(gowind 模板构建的 Go 服务,与 wind 同构)。
> 目标:用 wind 全新实现同等功能,作为脚手架的真实业务实例。

---

## 1. 参考项目功能面(实测梳理)

| 模块 | 内容 |
|---|---|
| **供应商 Provider** | CRUD + 分页/名称模糊过滤;字段:name、base_url、api_key(**出参脱敏**)、status;另有 **TestConnection**(探活供应商) |
| **模型 Model** | CRUD + 过滤(provider_id/display_name 模糊/status);字段:provider_id、**provider_name(关联查询)**、display_name、model_name、temperature、max_tokens、status;**Enable**(启用互斥:启用一个自动停用其他)+ GetEnabled |
| **内部 gRPC 域(llm)** | `LlmService.GetEnabledModel`(给其他服务取当前启用模型)、`ReportUsage`(用量回传,参考项目为空实现直接 success) |
| 部署形态 | 单服务 `app/admin`,grpc+rest 双 server;**PostgreSQL**;主键演进为应用侧雪花 ID(int64) |

## 2. 与纯 CRUD 生成器的差异(生成打底 vs 手工清单)

| 功能点 | wind generate 能否覆盖 | 处理 |
|---|---|---|
| 两表标准 CRUD + 分页 | ✅ 完全覆盖 | 生成后微调 |
| List 过滤(provider_id/display_name 模糊/status) | ⚠️ 部分 | proto 请求加字段,repo 手写 selector |
| provider_name 关联查询 | ❌ 单表生成 | repo 手写 join/二次查询 |
| Enable 互斥启用 | ❌ | repo 手写事务(全停→启用),service 暴露 |
| api_key 脱敏 | ❌ | service 出参掩码 |
| TestConnection | ❌ | service 手写(HTTP 探活 base_url) |
| 内部 gRPC 域(GetEnabledModel/ReportUsage) | ❌ | 手写 proto + service |
| 接口兼容参考项目(路由/报文,前端无缝) | ⚠️ | proto 照抄参考(admin 域),不用生成器默认路由 |

**路线决策(推荐 B):**
- **A. 纯生成路线**:`wind generate` 原生 CRUD 风格,最快(≈0.5d),但接口/路由与参考不同,前端不兼容
- **B. 兼容路线(推荐)**:proto 层照抄参考项目(admin/llm 两域,改动极小),生成器只打底(ent schema/repo/client/装配),service/server 手写适配参考接口(≈1d,含测试)

---

## 3. 前置输入(开工前需确认)

1. **PostgreSQL 连接串**(参考项目用 pg;库名/账号,或新建 `llm_admin` 库)
2. **项目名与 module**:建议目录 `E:/project/my-biz/llm-admin`,module `github.com/xianyu/llm-admin`(可改)
3. 确认走**路线 B**(接口兼容)还是路线 A

---

## 4. 实施步骤(路线 B)

### 阶段 0:脚手架与配置(0.5h)
1. `wind new llm-admin`(在工作区根执行,自动用本地模板)
2. `wind add service admin -s grpc,rest -d gorm`
3. `configs/bootstrap.yaml` 填真实基础设施(nacos 10.0.0.5:8848/xhf、OTLP 10.0.0.5:4317,dataId 用 `llm-admin` 避免与现网冲突)
4. `configs/data.yaml` 填 pg DSN
5. 建表(下方 DDL)→ `wind generate --dsn file://llm.sql --service admin --orm gorm` 打底
   (proto 会生成默认 CRUD 版,随后被参考版替换)

### 阶段 1:proto 对齐参考(1h)
6. 拷参考项目 4 个 proto 到本项目对应路径并适配:
   - `admin/service/v1/llm_provider.proto`、`llm_model.proto`(改 go_package 为本模块)
   - `admin/service/v1/admin_error.proto`(错误码)、`llm/service/v1/llm.proto`(内部域)
   - 路由照抄参考(`/llm/model/list` 等,前端无缝)
7. `wind api` 生成

### 阶段 2:业务定制(3~4h)
8. **repo 手写方法**(internal/data):
   - `LlmModelRepo.Enable`(事务:UPDATE 全表 status=停用 → 目标行启用)
   - `LlmModelRepo.GetEnabled`
   - List 过滤 selector:provider_id 精确 / display_name ILIKE / status
   - Provider 关联:Model List/Get 返回 provider_name(单条二次查询或 join)
9. **service 适配**:方法签名对齐参考 proto(Create/Update 返回 Info 而非 Empty 等);api_key 脱敏(`sk-***` 掩码);TestConnection 实现
10. **llm 内部域**:LlmService(GetEnabledModel 读 repo;ReportUsage 先空实现)
11. 每步 `wind api && go build ./...`

### 阶段 3:联调验收(1h)
12. 启动:注册到 nacos xhf(`llm-admin/admin-service`)、OTLP 上报、远端配置下发端口
13. curl 冒烟:Provider CRUD → Model CRUD → Enable → GetEnabledModel
14. `wind doctor --net` 全绿
15. (可选)前端指向新服务验证兼容性

### 验收清单
- [ ] 参考项目全部 RPC 在新项目等价可用(路由/报文一致)
- [ ] Enable 互斥语义正确(启用 A 后 B 自动停用)
- [ ] api_key 任何出参不泄露明文
- [ ] 生成器打底部分(ent schema/repo 骨架/装配)未被手写破坏,重复 generate 幂等
- [ ] nacos 注册 / 远端配置 / OTLP 三件套正常

---

## 5. 建表 DDL(PostgreSQL,供阶段 0 直接使用)

```sql
CREATE TABLE llm_provider (
  id         BIGSERIAL    PRIMARY KEY,
  name       VARCHAR(64)  NOT NULL DEFAULT '',
  base_url   VARCHAR(255) NOT NULL DEFAULT '',
  api_key    VARCHAR(255) NOT NULL DEFAULT '',
  status     SMALLINT     NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE llm_model (
  id           BIGSERIAL    PRIMARY KEY,
  provider_id  BIGINT       NOT NULL,
  display_name VARCHAR(64)  NOT NULL DEFAULT '',
  model_name   VARCHAR(128) NOT NULL DEFAULT '',
  temperature  REAL         NOT NULL DEFAULT 0.7,
  max_tokens   INT          NOT NULL DEFAULT 4096,
  status       SMALLINT     NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

> 雪花 ID:参考项目已迁应用侧雪花。实例第一期先用 BIGSERIAL 与生成器默认对齐;
> 雪花作为后置演进(实体 BeforeCreate + id 生成器),需要时照参考项目的迁移脚本走。

---

## 6. 工时预估

| 阶段 | 估时 |
|---|---|
| 阶段 0 脚手架与配置 | 0.5h |
| 阶段 1 proto 对齐 | 1h |
| 阶段 2 业务定制 | 3~4h |
| 阶段 3 联调验收 | 1h |
| **合计** | **≈ 6h** |
