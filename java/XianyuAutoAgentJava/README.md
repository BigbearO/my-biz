# XianyuAutoAgentJava

这是闲鱼自动回复机器人的Java版本，基于Spring Boot 3.5.6 + Spring WS + Langchain4j + MyBatis-Plus + PostgreSQL实现。

## 功能特性

1. WebSocket连接管理，实现实时消息收发
2. Token自动刷新机制
3. 基于LLM的智能回复系统，支持多Agent协作
4. 消息分类和意图路由
5. 对话历史管理和上下文维护
6. 商品信息缓存
7. 议价次数统计和管理
8. 人工接管模式支持
9. 心跳包机制保持连接活跃

## 技术栈

- Spring Boot 3.5.6
- Spring WS (WebService)
- Langchain4j 0.32.0
- MyBatis-Plus 3.5.5
- PostgreSQL
- Java-WebSocket
- Maven

## 环境要求

- JDK 17+
- Maven 3.6+
- PostgreSQL 14+

## 配置说明

1. 复制 `.env.example` 为 `.env` 并填写相关配置
2. 修改 `application.yml` 中的数据库连接信息
3. 配置LLM API密钥和模型信息

## 启动方式

### 开发环境

```bash
mvn spring-boot:run
```

### 生产环境

```bash
mvn clean package
java -jar target/XianyuAutoAgentJava-1.0.jar
```

### Docker部署

```bash
docker build -t xianyu-auto-agent .
docker run -p 8080:8080 xianyu-auto-agent
```

## 项目结构

```
src/main/java/com/xianyu/agent/
├── config/          # 配置类
├── controller/      # 控制器
├── model/           # 数据模型
├── mapper/          # MyBatis映射器
├── service/         # 业务逻辑
│   ├── agent/       # LLM Agent实现
│   └── impl/        # 服务实现
└── util/            # 工具类
```

## 主要功能模块

1. **WebSocket连接管理**：负责与闲鱼服务器建立和维护WebSocket连接
2. **消息处理**：解析和处理各种类型的消息
3. **对话管理**：维护对话历史和上下文
4. **智能回复**：使用LLM生成智能回复
5. **API交互**：与闲鱼API进行交互，获取商品信息和刷新token

## 人工接管

在对话中发送关键词"人工"、"接管"等可以切换人工模式，超时后会自动切回自动回复模式。

## 注意事项

1. 请确保配置正确的API密钥和模型URL
2. 首次启动会自动创建数据库表结构
3. 建议在生产环境中使用环境变量管理敏感信息
