# XianyuAutoAgentSpring

闲鱼自动回复助手的Spring Boot实现，基于Spring Boot 3.2.0和LangChain4j开发，支持WebSocket实时通信和AI自动回复功能。

## 功能特性

- 自动识别用户意图（价格、产品、物流等）
- 根据不同意图生成定制化回复
- 支持议价逻辑，根据议价次数调整回复策略
- WebSocket实时通信
- 支持人工模式切换
- SQLite数据库持久化存储聊天记录
- Token自动刷新机制
- 支持心跳检测和重连机制

## 技术栈

- Spring Boot 3.2.0
- Spring Data JPA
- Spring WebSocket
- LangChain4j 0.27.0
- SQLite
- JDK 17+

## 项目结构

项目采用阿里四层架构设计：

```
com.xianyu.autoagent
├── api             # 应用层，包含控制器和WebSocket处理器
├── service         # 领域服务层，定义核心业务接口
├── domain          # 领域模型层，包含实体和仓库接口
└── infrastructure  # 基础设施层，实现具体服务和工具类
```

## 快速开始

### 前置条件

- JDK 17或更高版本
- Maven 3.6或更高版本
- 一个支持OpenAI API兼容接口的AI服务（如DashScope）

### 配置

1. 复制`application.properties`文件，根据实际情况修改配置：

```properties
# AI模型配置
xianyu.ai.api-key=your-api-key
xianyu.ai.base-url=your-api-base-url

# WebSocket配置
xianyu.ws.heartbeat-interval=30000
xianyu.ws.max-retry-count=3

# 消息配置
xianyu.message.expire-time=300000
```

### 构建和运行

使用Maven构建项目：

```bash
mvn clean install
```

运行应用：

```bash
mvn spring-boot:run
```

或直接运行编译后的jar文件：

```bash
java -jar target/XianyuAutoAgentSpring.jar
```

## API文档

### WebSocket API

WebSocket连接端点：`ws://localhost:8080/ws`

支持的消息类型：
- `chat` - 聊天消息
- `heartbeat` - 心跳检测
- `refresh-token` - 刷新Token
- `manual-mode` - 切换人工模式

### REST API

#### 聊天相关

- `GET /api/chat/history` - 获取聊天历史
- `POST /api/chat/reply` - 生成自动回复
- `POST /api/chat/message` - 保存消息
- `POST /api/chat/clean-expired` - 清理过期消息

#### 商品相关

- `GET /api/item/{itemId}` - 获取商品信息
- `POST /api/item/save` - 保存或更新商品信息
- `DELETE /api/item/{itemId}` - 删除商品

#### 闲鱼相关

- `POST /api/xianyu/connect` - 初始化连接
- `POST /api/xianyu/disconnect` - 断开连接
- `POST /api/xianyu/refresh-token` - 刷新Token
- `POST /api/xianyu/manual-mode` - 切换人工模式

## 提示词配置

提示词文件位于`src/main/resources/prompts/`目录下：

- `default_prompt.txt` - 默认回复提示词
- `price_prompt.txt` - 议价回复提示词
- `intent_prompt.txt` - 意图识别提示词

## 许可证

[MIT License](LICENSE)