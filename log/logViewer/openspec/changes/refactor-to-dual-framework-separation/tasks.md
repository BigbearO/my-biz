## 1. 目录结构规划
- [x] 1.1 创建 gin/ 和 gozero/ 目录
- [x] 1.2 设计目录结构（配置、入口、路由、处理器）
- [x] 1.3 规划共享代码的复用方式

## 2. Gin 框架代码迁移
- [x] 2.1 移动 main.go → gin/main.go
- [x] 2.2 移动 api/ → gin/api/
- [x] 2.3 复制 application/ → gin/application/
- [x] 2.4 创建 etc/gin/config.yaml（从 etc/config.yaml 迁移）

## 3. go-zero 框架代码迁移
- [x] 3.1 移动 zero_main.go → gozero/main.go
- [x] 3.2 移动 internal/ → gozero/internal/
- [x] 3.3 移动 api/logviewer.api → gozero/api/
- [x] 3.4 创建 etc/gozero/logviewer.yaml（从 etc/logviewer.yaml 迁移）

## 4. 共享基础设施层重构
- [x] 4.1 重构 ES 查询逻辑为框架无关的函数
- [x] 4.1.1 提取 `BuildESQuery` 函数（从 SearchLogsV2 提取查询构建逻辑）
- [x] 4.1.2 提取 `ExecuteSearch` 函数（从 SearchLogsV2 提取查询执行逻辑）
- [x] 4.1.3 提取 `buildMessageBool`、`parseTime` 等辅助函数
- [x] 4.2 评估 application/ 是否需要共享
- [x] 4.3 更新 import 路径

## 5. 配置文件调整
- [x] 5.1 更新 gin/config.yaml 中的路径引用
- [x] 5.2 更新 gozero/logviewer.yaml 中的路径引用
- [x] 5.3 确保静态文件路径一致

## 6. 启动脚本和文档
- [x] 6.1 创建启动脚本（如需要）
- [x] 6.2 更新 README 说明不同框架的启动方式
- [x] 6.3 更新 openspec/project.md 架构描述

## 7. 验证和测试
- [x] 7.1 测试 Gin 框架启动和功能
- [x] 7.2 测试 go-zero 框架启动和功能
- [x] 7.3 验证静态资源访问
- [x] 7.4 确认两个框架可以独立运行
