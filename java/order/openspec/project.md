# Project Context

## Purpose
代练订单管理系统，用于管理多平台代练订单，支持Excel批量导入、闲鱼订单自动同步、会员时长自动计算等功能。系统主要服务于游戏代练业务场景，管理用户会员时长和订单信息。

## Tech Stack

### 核心框架
- **Spring Boot 3.8.3** - 应用框架
- **Spring Cloud Alibaba** - 微服务治理（Nacos注册中心、配置中心）

### 持久层
- **MyBatis Plus** - ORM框架
- **MySQL 8.0** - 关系型数据库
- **MySQL Connector/J** - MySQL驱动

### 消息队列
- **Apache RocketMQ 2.3.1** - 消息队列（预留接口，暂不使用）

### 工具库
- **Hutool 5.8.38** - Java工具类库
- **MapStruct 1.5.5.Final** - 对象映射框架
- **Lombok 1.18.36** - 代码简化工具

### 安全与认证
- **Pig Security** - 安全模块（基于pig框架）

### 服务治理
- **Nacos Discovery** - 服务注册与发现
- **Nacos Config** - 配置中心
- **Pig Feign** - 服务间调用

### 日志与监控
- **Pig Log** - 日志处理模块

### 文档
- **Pig Swagger** - API文档

### 容器化
- **Docker** - 容器化部署
- **Undertow** - Web服务器

### 支持的游戏
- **原神** (game_type: 0)
- **星穹铁道** (game_type: 1)

## Project Conventions

### Code Style
- **命名规范**：
  - 类名：大驼峰命名法（PascalCase）
  - 方法名/变量名：小驼峰命名法（camelCase）
  - 常量：全大写+下划线（UPPER_SNAKE_CASE）
  - 数据库表名/字段：小写+下划线（snake_case）
- **包结构**：按功能模块划分，遵循领域驱动设计（DDD）
- **注解使用**：优先使用Lombok简化代码
- **异常处理**：统一使用BusinessException处理业务异常

### Architecture Patterns
- **分层架构**：Controller → Service → Mapper → Database
- **多租户隔离**：所有表包含tenant_id字段，查询时自动过滤
- **仓储模式**：使用Manager层封装数据访问逻辑
- **DTO/VO分离**：
  - DTO用于数据传输（接收请求）
  - VO用于视图展示（返回响应）
  - Entity对应数据库实体

### Testing Strategy
- 单元测试覆盖核心业务逻辑
- 集成测试覆盖API接口
- 使用Spring Boot Test框架

### Git Workflow
- **主分支**：master（生产环境）
- **开发分支**：develop（开发环境）
- **功能分支**：feature/功能名
- **提交规范**：feat/fix/docs/refactor/test/chore

## Domain Context

### 核心业务概念
1. **代练订单**：用户购买的代练服务，包含订单号、平台、用户账号、商品、支付时间等
2. **游戏账号**：系统分配的代练账号，包含账号编号、用户账号、游戏类型、会员时长等
3. **商品SKU**：商品规格配置，定义不同平台商品对应的游戏类型和会员天数
4. **会员时长计算**：
   - 账号不存在：创建新账号，从支付时间开始计算
   - 账号存在且在会员期间（expire_time > now）：累加到现有过期时间
   - 账号存在但不在会员期间：从当前时间开始计算

### 业务流程
1. **Excel导入订单**：
   - 读取Excel文件
   - 验证数据
   - 查询SKU配置
   - 创建订单
   - 计算并更新账号时长
   - 记录时长变更日志

2. **闲鱼订单同步**：
   - 定时（每小时）或手动触发
   - 调用闲鱼开放API
   - 过滤已存在的订单
   - 创建订单并更新时长
   - 记录同步日志

3. **账号过期扫描**：
   - 每天凌晨2点执行
   - 查询7天内即将过期的账号
   - 按游戏类型分组
   - 发送到MQ（预留接口）

## Important Constraints

### 技术约束
- 基于Pig微服务框架，遵循其规范
- 使用MySQL作为主数据库，不支持其他数据库
- 使用MyBatis Plus进行数据访问
- 必须支持多租户数据隔离

### 业务约束
- 订单导入后不进行审核，默认状态为"已支付"
- 订单号必须唯一，重复订单会被忽略
- 游戏账号凭证后续录入，不包含在导入流程中
- 账号过期不做处理，只做筛选，实际处理在每日任务中
- MQ功能预留，当前版本不使用

### 数据约束
- 所有表必须包含：id（主键）、create_time、update_time、del_flag（逻辑删除）、tenant_id（多租户）
- 订单表order_no字段必须有唯一索引
- 商品SKU表sku_id和(platform,commodity_id)必须有唯一索引
- 游戏账号表account_no必须有唯一索引

## External Dependencies

### 闲鱼开放API
- **用途**：获取闲鱼平台订单数据
- **认证方式**：AppKey + AppSecret
- **同步频率**：默认60分钟，可配置
- **API地址**：https://open-api.xianyu.com（示例）

### Nacos
- **注册中心**：服务注册与发现
- **配置中心**：统一配置管理

### MySQL数据库
- **用途**：持久化存储订单、账号、配置等数据
- **版本**：MySQL 8.0+

### RocketMQ（预留）
- **用途**：异步处理账号过期事件
- **状态**：已集成但暂不使用

## Database Schema
- game_type_config（游戏类型配置表）
- commodity_sku（商品SKU配置表）
- platform_config（平台配置表）
- trade_order（订单表）
- game_account（游戏账号表）
- account_duration_record（账号时长记录表）
- order_sync_log（订单同步日志表）
