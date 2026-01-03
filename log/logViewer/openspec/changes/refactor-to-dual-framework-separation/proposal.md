# 变更: 重构为双框架分离架构

## Why
当前项目 Gin 和 go-zero 代码混在一起，结构不清晰。需要将两个框架的代码分离到独立目录，每个框架有自己完整的入口、配置和路由，保持单个项目但架构清晰。

## What Changes
- 创建 gin/ 目录，包含 Gin 框架的完整代码
- 创建 gozero/ 目录，包含 go-zero 框架的完整代码
- 重构 main.go → gin/main.go（Gin 入口）
- 重构 zero_main.go → gozero/main.go（go-zero 入口）
- 分离配置文件：etc/gin/ 和 etc/gozero/
- 共享基础设施层（infra/es/）
- 共享前端静态资源（static/）

## Impact
- 受影响的规范: 架构规范
- 受影响的代码:
  - main.go → gin/main.go
  - zero_main.go → gozero/main.go
  - api/ → gin/api/
  - internal/ → gozero/internal/
  - etc/ → etc/gin/ 和 etc/gozero/
  - application/ → 需要评估是否共享或复制到各框架目录

**关键设计决策:**
- ES 查询构建逻辑（`buildMessageBool`、查询参数结构、时间解析等）必须位于 `infra/es/` 层，两个框架共享使用
- 每个框架的 Handler/Logic 层通过调用 `infra/es/` 的查询构建函数，避免重复实现
- ES 客户端初始化统一在 `infra/es/` 中，配置通过各框架的配置文件传入
