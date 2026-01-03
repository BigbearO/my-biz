## Context
当前项目根目录存在两套框架代码：
- **Gin 框架**: main.go, api/, application/, etc/config.yaml
- **go-zero 框架**: zero_main.go, internal/, api/logviewer.api, etc/logviewer.yaml

问题：
- 两套代码混在根目录，结构不清晰
- 入口文件不统一（main.go vs zero_main.go）
- 配置分散在 etc/ 目录
- 难以区分哪个框架负责哪些功能

目标：保留两个框架但分离到独立目录，提高代码组织性和可维护性。

## Goals / Non-Goals

**Goals:**
- 将 Gin 和 go-zero 代码分离到独立目录
- 每个框架目录包含完整的入口、配置、路由、处理器
- 共享基础设施层（如 ES 客户端）避免代码重复
- 保持单个项目，便于统一管理
- 两个框架可以独立启动和运行

**Non-Goals:**
- 不删除任何框架或功能
- 不改变业务逻辑
- 不改变 API 接口
- 不引入新框架

## Decisions

**Decision 1: 采用框架目录分离**
- **理由**:
  - 每个框架目录包含完整的 MVC 结构
  - 清晰的边界，易于维护和理解
  - 可以独立测试和部署
- **目录结构**:
  ```
  project/
  ├── gin/               # Gin 框架代码
  │   ├── main.go        # Gin 入口
  │   ├── api/           # Gin 处理器
  │   ├── application/   # Gin 业务逻辑
  │   └── etc/           # Gin 配置
  ├── gozero/            # go-zero 框架代码
  │   ├── main.go        # go-zero 入口
  │   ├── internal/      # go-zero 内部代码
  │   ├── api/           # go-zero API 定义
  │   └── etc/           # go-zero 配置
  ├── infra/             # 共享基础设施（ES 客户端）
  ├── static/            # 共享静态资源
  └── etc/               # 保留根目录配置（如需要）
  ```

**Decision 2: 共享基础设施层（infra/）**
- **理由**:
  - ES 客户端初始化逻辑相同，避免重复
  - 配置结构一致，可复用
  - ES 查询构建逻辑复杂，两个框架应共享同一套实现
- **实现方式**:
  - `infra/es/` 包含以下共享功能：
    - ES 客户端初始化（`NewESClient`）
    - 查询参数结构体（`QueryParam`、`Tag`、`LogDoc`）
    - ES 查询构建函数（`buildMessageBool`）
    - 时间解析函数（`parseTime`）
    - 响应结构体（`Resp`）
  - 两个框架通过 `import "logViewer/infra/es"` 引用共享代码
  - **重要**: `SearchLogsV2` 函数当前依赖 Gin 的 `gin.Context`，需要重构为不依赖特定框架的纯业务逻辑函数

**Decision 3: 独立配置文件**
- **理由**:
  - 两个框架配置格式不同
  - 便于独立调优和部署
- **配置路径**:
  - etc/gin/config.yaml（Gin 配置）
  - etc/gozero/logviewer.yaml（go-zero 配置）

**Decision 4: application/ 层的处理**
- **理由**: application/ 当前主要为 Gin 使用，go-zero 有自己的 logic 层
- **方案**: 复制到 gin/ 目录，go-zero 不使用
- **替代方案**: 如果需要共享，可以提取到 common/ 目录

**Decision 5: ES 查询逻辑去框架化**
- **理由**: 当前 `SearchLogsV2` 函数依赖 `gin.Context`，go-zero 无法直接使用
- **方案**:
  1. 将 `SearchLogsV2` 重构为纯业务逻辑函数（不依赖具体框架）
  2. 提取查询构建逻辑为独立函数（`BuildESQuery`）
  3. 提取查询执行和结果解析为独立函数（`ExecuteSearch`）
  4. Gin 和 go-zero 的 Handler 层各自调用这些共享函数，处理框架特定的响应
- **函数签名设计**:
  ```go
  // infra/es/query.go - 共享的查询构建逻辑
  func BuildESQuery(qp QueryParam, indexPrefix string) (map[string]interface{}, error)

  // infra/es/search.go - 共享的查询执行逻辑
  func ExecuteSearch(ctx context.Context, client *elasticsearch.Client, query map[string]interface{}, indexPattern string) ([]LogDoc, int64, error)
  ```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Import 路径变更 | 大量文件需要更新 import | 使用编辑器批量替换 |
| 配置路径错误 | 启动时找不到配置 | 仔细检查配置文件路径 |
| 静态资源路径问题 | 404 错误 | 统一使用相对路径 ./static |
| 共享代码修改冲突 | 修改 infra/ 影响两个框架 | 添加注释说明共享范围 |

## Migration Plan

**步骤 1: 创建目录结构**
```bash
mkdir -p gin/api gin/application etc/gin
mkdir -p gozero/internal/handler gozero/internal/svc gozero/internal/config gozero/api etc/gozero
```

**步骤 2: 迁移 Gin 代码**
- 移动 main.go → gin/main.go
- 移动 api/ → gin/api/
- 移动 application/ → gin/application/
- 移动 etc/config.yaml → etc/gin/config.yaml

**步骤 3: 迁移 go-zero 代码**
- 移动 zero_main.go → gozero/main.go
- 移动 internal/ → gozero/internal/
- 移动 api/logviewer.api → gozero/api/logviewer.api
- 移动 etc/logviewer.yaml → etc/gozero/logviewer.yaml

**步骤 4: 更新 import 路径**
- 更新 gin/main.go: `import "logViewer/infra/es"`
- 更新 gin/api/: `import "logViewer/infra/es"`
- 更新 gozero/main.go: `import "logViewer/gozero/internal/..."`
- 更新 gozero/internal/: `import "logViewer/infra/es"`

**步骤 5: 更新 go.mod**
- 添加新的 package 路径声明（如需要）
- 运行 `go mod tidy`

**步骤 6: 验证**
```bash
# 启动 Gin
cd gin && go run main.go -f ../etc/gin/config.yaml

# 启动 go-zero
cd gozero && go run main.go -f ../etc/gozero/logviewer.yaml
```

**回滚计划**
- Git 提交所有变更，便于回退
- 在删除原文件前确认新目录可用

## Open Questions
- [ ] application/ 是否需要共享给 go-zero？
- [ ] 是否需要创建根级别的 Makefile 或启动脚本？
- [ ] etc/ 目录是否还需要保留（除 gin/ 和 gozero/ 子目录）？
- [ ] 两个框架是否需要监听不同端口？
