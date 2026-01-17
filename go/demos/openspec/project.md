# 项目背景

## 项目目的
这是一个基于 Go 语言和 Kratos 框架的微服务演示项目，主要用于：
- 演示如何使用 Kratos 框架构建微服务应用
- 集成 Nacos 作为服务注册中心和配置中心
- 实现动态配置监听和加载功能
- 提供 gRPC 和 HTTP 双协议支持的服务示例
- 探索微服务架构的最佳实践

## 技术栈

### 核心技术
- **Go 1.22+**: 主要编程语言
- **Kratos v2**: 微服务框架，提供服务治理、配置管理、日志等基础能力
- **Nacos**: 阿里巴巴开源的服务发现、配置管理和服务管理平台
- **gRPC**: Google 开源的高性能 RPC 框架
- **Protobuf**: Google 的数据交换格式，用于接口定义
- **Wire**: Google 的依赖注入框架

### 关键依赖
- `github.com/go-kratos/kratos/v2`: Kratos 核心框架
- `github.com/go-kratos/kratos/contrib/registry/nacos/v2`: Kratos Nacos 注册中心集成
- `github.com/nacos-group/nacos-sdk-go`: Nacos Go 语言 SDK
- `github.com/google/wire`: 依赖注入工具
- `go.uber.org/automaxprocs`: 自动优化 GOMAXPROCS

### 工具链
- **Make**: 项目构建自动化
- **Docker**: 容器化部署支持
- **Protobuf Compiler**: 接口定义编译

## 项目规范

### 代码风格
- 遵循 Go 官方编码规范 [Effective Go](https://golang.org/doc/effective_go.html)
- 使用 `gofmt` 或 `goimports` 格式化代码
- 采用标准的 Go 项目目录结构（cmd, internal, api, configs 等）
- 文件名使用小写字母，多个单词用连字符连接
- 导出函数和类型必须有清晰的英文注释

### 架构模式
- **整洁架构（Clean Architecture）**: 采用 Kratos 推荐的分层架构
  - `api`: 接口定义层（Protobuf）
  - `cmd`: 应用入口层
  - `internal`: 内部实现
    - `biz`: 业务逻辑层（Use Case）
    - `data`: 数据访问层（Repository）
    - `service`: 服务实现层
    - `server`: 服务器配置层
    - `conf`: 配置定义层
- **依赖注入**: 使用 Wire 框架管理依赖关系
- **配置驱动**: 所有配置通过配置文件管理，支持动态加载
- **接口隔离**: 通过 Protobuf 定义清晰的 API 契约

### 配置管理
- 配置文件存放在 `configs/` 目录
- 支持本地文件配置和 Nacos 远程配置
- 使用 Kratos 的配置管理抽象层
- 敏感信息（密码、密钥）通过环境变量或配置中心管理

### 服务注册与发现
- 使用 Nacos 作为服务注册中心
- 服务启动时自动注册到 Nacos
- 支持健康检查和心跳机制
- 配置参数全部从配置文件读取，无硬编码

### 日志规范
- 使用 Kratos 的统一日志接口
- 日志格式为结构化 JSON
- 包含关键字段：时间戳、调用者、服务 ID、追踪 ID 等
- 支持日志级别控制

### 错误处理
- 使用 Kratos 的错误处理机制
- 定义业务错误码和错误原因
- 错误信息清晰明确，便于排查问题
- 在 API 层统一处理错误响应

### 测试策略
- 单元测试覆盖核心业务逻辑
- 集成测试验证服务间交互
- API 测试确保接口契约符合预期
- 使用 Mock 技术隔离外部依赖

### Git 工作流
- 主分支：`master`
- 功能开发：从 master 创建 feature 分支
- 分支命名：`feature/功能描述` 或 `fix/问题描述`
- 提交信息：清晰描述变更内容，遵循约定式提交规范
- 代码审查：所有变更需经过代码审查
- 版本标签：使用语义化版本号（v1.0.0）

## 领域上下文

### 服务能力
- **Greeter 服务**: 基础的问候服务，演示 gRPC 和 HTTP 双协议支持
- **配置管理**: 动态加载和监听配置变更
- **服务注册**: 集成 Nacos 实现服务自动注册和发现

### 核心概念
- **Bootstrap**: 应用启动配置，包含服务器、数据、注册中心等配置
- **Server**: 支持 HTTP 和 gRPC 两种传输协议
- **Registry**: 服务注册中心抽象，当前实现 Nacos 注册
- **Config**: 配置管理抽象，支持本地文件和远程配置

## 重要约束

### 技术约束
- Go 版本必须 >= 1.22
- Kratos 版本 v2.x
- Nacos 服务端版本 >= 2.0
- 服务必须支持优雅关闭

### 性能约束
- HTTP 服务默认监听 8000 端口
- gRPC 服务默认监听 9000 端口
- 支持连接复用和请求超时配置

### 安全约束
- 配置文件中不存储生产环境敏感信息
- 服务间通信支持 TLS 加密（可选）
- 接口访问需要身份验证（根据需求）

### 部署约束
- 支持 Docker 容器化部署
- 支持 Kubernetes 编排
- 支持 12-Factor 应用规范

## 外部依赖

### Nacos 服务
- **用途**: 服务注册、配置管理
- **地址**: 可配置，支持多个服务端节点
- **认证**: 支持用户名密码认证
- **命名空间**: 支持多环境隔离

### 监控和可观测性（可选）
- **Prometheus**: 指标收集
- **OpenTelemetry**: 分布式追踪
- **日志收集**: 支持 ELK 或类似方案

### 数据库（预留）
- 当前项目为演示项目，未集成实际数据库
- 预留 data 层接口，便于后续扩展
