## ADDED Requirements

### Requirement: 项目架构规范
项目 MUST 采用双框架分离架构，Gin 和 go-zero 框架代码位于独立目录，每个框架目录包含完整的入口、配置、路由和处理器，基础设施层（infra/）和静态资源（static/）为共享层。

#### Scenario: 项目目录结构
- **WHEN** 用户查看项目根目录
- **THEN** 应看到以下目录结构：
  - `gin/` - Gin 框架完整代码
  - `gozero/` - go-zero 框架完整代码
  - `infra/` - 共享基础设施层
  - `static/` - 共享静态资源
  - `etc/gin/` - Gin 配置文件
  - `etc/gozero/` - go-zero 配置文件

#### Scenario: Gin 框架独立运行
- **WHEN** 开发者切换到 gin 目录并执行 `go run main.go -f ../etc/gin/config.yaml`
- **THEN** Gin 服务必须成功启动并监听配置的端口
- **AND** 所有 Gin 路由必须正常工作

#### Scenario: go-zero 框架独立运行
- **WHEN** 开发者切换到 gozero 目录并执行 `go run main.go -f ../etc/gozero/logviewer.yaml`
- **THEN** go-zero 服务必须成功启动并监听配置的端口
- **AND** 所有 go-zero 路由必须正常工作

#### Scenario: 共享基础设施层
- **WHEN** 两个框架代码都导入 `logViewer/infra/es`
- **THEN** 必须共享同一个 ES 客户端实现
- **AND** 避免代码重复

#### Scenario: ES 查询构建逻辑共用
- **WHEN** 框架需要执行 ES 查询
- **THEN** 必须调用 `infra/es` 层的 `BuildESQuery` 函数构建查询
- **AND** 必须调用 `infra/es` 层的 `ExecuteSearch` 函数执行查询
- **AND** 不得在框架层重复实现查询构建逻辑

#### Scenario: 框架层只处理 HTTP 响应
- **WHEN** Gin 框架的 Handler 处理请求
- **THEN** 只负责参数绑定、调用共享的查询函数、使用 `gin.Context` 返回 JSON 响应
- **WHEN** go-zero 框架的 Handler 处理请求
- **THEN** 只负责参数绑定、调用共享的查询函数、使用 go-zero 的 Response 返回
- **AND** 查询逻辑完全位于 `infra/es` 层

### Requirement: 配置文件分离
项目 MUST 将配置文件按框架分离到独立目录，Gin 使用 etc/gin/config.yaml，go-zero 使用 etc/gozero/logviewer.yaml。

#### Scenario: 配置文件路径正确
- **WHEN** Gin 服务启动
- **THEN** 必须从 etc/gin/config.yaml 读取配置
- **WHEN** go-zero 服务启动
- **THEN** 必须从 etc/gozero/logviewer.yaml 读取配置

#### Scenario: 静态资源路径
- **WHEN** 任一框架配置静态文件服务
- **THEN** 必须使用相对路径 ./static 引用共享的静态资源目录

### Requirement: 代码导入规范
项目 MUST 在迁移后正确更新所有 import 路径，确保跨目录引用正确。

#### Scenario: Gin 导入共享层
- **WHEN** Gin 代码导入 ES 客户端和查询函数
- **THEN** 必须使用 `logViewer/infra/es` 路径
- **AND** 调用 `BuildESQuery` 和 `ExecuteSearch` 等共享函数

#### Scenario: go-zero 导入共享层
- **WHEN** go-zero 代码导入 ES 客户端和查询函数
- **THEN** 必须使用 `logViewer/infra/es` 路径
- **AND** 调用 `BuildESQuery` 和 `ExecuteSearch` 等共享函数

#### Scenario: Gin 导入共享层
- **WHEN** Gin 代码导入 ES 客户端
- **THEN** 必须使用 `logViewer/infra/es` 路径

#### Scenario: go-zero 导入共享层
- **WHEN** go-zero 代码导入 ES 客户端
- **THEN** 必须使用 `logViewer/infra/es` 路径

#### Scenario: go-zero 内部导入
- **WHEN** go-zero 代码导入内部模块
- **THEN** 必须使用 `logViewer/gozero/internal/...` 路径

## MODIFIED Requirements

### Requirement: 项目入口规范
项目 MUST 提供两个独立的入口文件，分别位于 gin/main.go 和 gozero/main.go。

#### Scenario: Gin 入口启动
- **WHEN** 用户执行 `cd gin && go run main.go -f ../etc/gin/config.yaml`
- **THEN** Gin 服务必须启动并提供日志查询和健康检查接口

#### Scenario: go-zero 入口启动
- **WHEN** 用户执行 `cd gozero && go run main.go -f ../etc/gozero/logviewer.yaml`
- **THEN** go-zero 服务必须启动并提供日志查询和健康检查接口

### Requirement: 项目启动说明
项目 MUST 在文档中说明如何启动不同的框架。

#### Scenario: 查看启动文档
- **WHEN** 开发者查看项目文档
- **THEN** 必须清楚地说明 Gin 和 go-zero 的启动方式和命令
- **AND** 说明两个框架的配置文件位置
