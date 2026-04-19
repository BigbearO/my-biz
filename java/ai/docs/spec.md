# 3D 数字人后端 — Java Spring Boot 迁移提案

> 基于 Python FastAPI 现有实现，迁移至 Java + Spring Boot + Spring AI 技术栈

---

## 一、现有接口盘点

当前 Python 后端共暴露 **5 个 API 端点**：

| # | 方法 | 路径 | 功能 | 请求方式 | 响应类型 |
|---|------|------|------|----------|----------|
| 1 | GET | `/api/health` | 健康检查 | - | JSON |
| 2 | POST | `/api/chat` | 流式聊天（SSE） | JSON Body | `text/plain` 流 |
| 3 | GET | `/api/tts` | 文本转语音 | Query Params | `audio/mpeg` 流 |
| 4 | GET | `/api/tts/voices` | 可用语音列表 | - | JSON |
| 5 | GET | `/api/models` | 可用模型列表 | - | JSON |

### 1.1 健康检查

```
GET /api/health
```

**响应 200：**

```json
{
  "status": "ok",
  "model": "qwen2.5:7b"
}
```

**响应 503：** Ollama 不可用时

```json
{
  "detail": "Ollama 服务不可用: <error>"
}
```

### 1.2 流式聊天

```
POST /api/chat
Content-Type: application/json
```

**请求体：**

```json
{
  "message": "你好",
  "history": [
    { "role": "user", "content": "上一轮问题" },
    { "role": "assistant", "content": "上一轮回答" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | string | 是 | 当前用户消息 |
| `history` | ChatMessage[] | 否 | 对话历史 |

**响应：** `Content-Type: text/plain; charset=utf-8`，逐字符流式返回

### 1.3 文本转语音

```
GET /api/tts?text=要合成的文本&voice=zh-CN-XiaoxiaoNeural&rate=%2B0%25&pitch=%2B0Hz
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `text` | string | 是 | - | 要合成的文本 |
| `voice` | string | 否 | `zh-CN-XiaoxiaoNeural` | 语音名称 |
| `rate` | string | 否 | `+0%` | 语速，如 `+10%`、`-5%` |
| `pitch` | string | 否 | `+0Hz` | 音调 |

**响应：** `Content-Type: audio/mpeg`，流式返回 MP3 音频

### 1.4 语音列表

```
GET /api/tts/voices
```

**响应：**

```json
{
  "current": "zh-CN-XiaoxiaoNeural",
  "voices": [
    { "name": "zh-CN-XiaoxiaoNeural", "gender": "Female", "locale": "zh-CN" }
  ]
}
```

### 1.5 模型列表

```
GET /api/models
```

**响应：**

```json
{
  "current": "qwen2.5:7b",
  "available": ["qwen2.5:7b", "llama3:8b"]
}
```

---

## 二、Java + Spring Boot + Spring AI 技术方案

### 2.1 技术选型

| 层次 | 技术 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.3+ | JDK 17+ |
| AI 集成 | Spring AI 1.0+ | 统一 Chat Client，支持 Ollama / OpenAI 等多后端 |
| TTS | Edge TTS (WebSocket) | 通过 Reactor Netty WebSocket 调用微软 Edge TTS 公开接口 |
| 流式响应 | Spring WebFlux (SSE) | Chat 使用 `Flux<String>`，TTS 使用 `Flux<byte[]>` |
| 配置 | application.yml + @ConfigurationProperties | 类型安全配置 |
| API 文档 | SpringDoc OpenAPI (Swagger) | 自动生成 OpenAPI 3.0 文档 |

### 2.2 项目结构

```
src/main/java/com/avatar3d/
├── Avatar3dApplication.java              # 启动类
├── config/
│   ├── AppProperties.java                # @ConfigurationProperties 统一配置
│   ├── SpringAiConfig.java               # Spring AI ChatClient 配置
│   └── WebConfig.java                    # CORS 配置
├── controller/
│   ├── HealthController.java             # /api/health
│   ├── ChatController.java               # /api/chat
│   ├── TtsController.java                # /api/tts, /api/tts/voices
│   └── ModelController.java              # /api/models
├── service/
│   ├── ChatService.java                  # Spring AI 流式聊天
│   ├── EdgeTtsService.java               # Edge TTS WebSocket 调用
│   └── VoiceListService.java             # 语音列表获取
├── model/
│   ├── ChatRequest.java                  # 聊天请求 DTO
│   ├── ChatMessage.java                  # 聊天消息 DTO
│   ├── HealthResponse.java               # 健康检查响应
│   ├── VoiceInfo.java                    # 语音信息
│   └── ModelListResponse.java            # 模型列表响应
└── exception/
    ├── GlobalExceptionHandler.java       # 全局异常处理
    └── ServiceUnavailableException.java  # 503 异常
```

### 2.3 核心依赖 (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot WebFlux（流式响应） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring AI + Ollama -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    </dependency>

    <!-- SpringDoc OpenAPI -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
        <version>2.6.0</version>
    </dependency>

    <!-- Lombok（可选） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2.4 配置文件 (application.yml)

```yaml
server:
  port: 8000

app:
  cors:
    origins: http://localhost:5173
  ai:
    model: qwen2.5:7b
    base-url: http://localhost:11434
    system-prompt: >
      你是一个友好的3D数字人助手。请用简洁、自然的中文回答用户的问题。
      回答时尽量控制在200字以内，语气亲切自然。
  tts:
    trusted-client-token: 6A5AA1D4EAFF4E9FB37E23D68491D6F0
    voice: zh-CN-XiaoxiaoNeural
    rate: "+0%"
    pitch: "+0Hz"
    connect-timeout: 5000
    read-timeout: 30000

spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        options:
          temperature: 0.7
```

### 2.5 关键实现说明

#### Chat 接口 — Spring AI 流式

```java
// ChatService.java
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder, AppProperties props) {
        this.chatClient = builder
            .defaultSystem(props.getAi().getSystemPrompt())
            .build();
    }

    public Flux<String> streamChat(String message, List<ChatMessage> history) {
        Prompt prompt = buildPrompt(message, history);
        return chatClient.prompt(prompt)
            .stream()
            .content();
    }
}
```

```java
// ChatController.java
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return chatService.streamChat(request.message(), request.history());
    }
}
```

#### TTS 接口 — Edge TTS WebSocket

使用 Reactor Netty 的 WebSocketClient 直接与微软 Edge TTS 通讯，返回 `Flux<byte[]>` 流式 MP3。实现逻辑参考 `docs/tts.md` 中的 WebSocket 协议。

#### 前端兼容性

迁移后所有 API 路径、请求/响应格式保持不变，前端无需修改。默认端口从 8000 不变。

---

## 三、OpenAPI 3.0 规范

```yaml
openapi: 3.0.3
info:
  title: 3D数字人后端
  description: |
    3D数字人后端服务，提供AI对话（流式）、文本转语音（TTS）、语音列表、模型管理等功能。

    技术栈：Java 17 + Spring Boot 3.3 + Spring AI 1.0 + WebFlux
  version: 2.0.0
  contact:
    name: 3D数字人团队

servers:
  - url: http://localhost:8000
    description: 本地开发

tags:
  - name: 系统
    description: 系统级接口
  - name: 对话
    description: AI 聊天对话
  - name: 语音
    description: TTS 语音合成
  - name: 模型
    description: AI 模型管理

paths:
  /api/health:
    get:
      tags: [系统]
      summary: 健康检查
      description: 检查服务状态及 Ollama 连接是否正常
      operationId: healthCheck
      responses:
        '200':
          description: 服务正常
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HealthResponse'
              example:
                status: ok
                model: "qwen2.5:7b"
        '503':
          description: Ollama 服务不可用
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
              example:
                detail: "Ollama 服务不可用: Connection refused"

  /api/chat:
    post:
      tags: [对话]
      summary: 流式聊天
      description: |
        发送用户消息，流式返回 AI 回复。

        响应为 `text/plain` 流式文本，逐 token 返回。
        前端通过 `fetch` + `ReadableStream` 读取。
      operationId: chat
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ChatRequest'
            example:
              message: "你好，请介绍一下你自己"
              history:
                - role: user
                  content: "今天天气怎么样"
                - role: assistant
                  content: "今天天气不错，阳光明媚"
      responses:
        '200':
          description: 流式文本响应
          content:
            text/plain:
              schema:
                type: string
                description: 逐 token 流式返回的 AI 回复文本
        '400':
          description: 请求参数错误
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/tts:
    get:
      tags: [语音]
      summary: 文本转语音
      description: |
        将文本合成为 MP3 音频，流式返回。

        通过 Edge TTS (WebSocket) 调用微软公开接口，无需 API Key。
      operationId: textToSpeech
      parameters:
        - name: text
          in: query
          required: true
          description: 要合成的文本（建议不超过 300 字）
          schema:
            type: string
          example: "你好！很高兴为你服务。"
        - name: voice
          in: query
          required: false
          description: 语音名称
          schema:
            type: string
            default: "zh-CN-XiaoxiaoNeural"
          example: "zh-CN-YunxiNeural"
        - name: rate
          in: query
          required: false
          description: "语速，格式如 +10%、-5%"
          schema:
            type: string
            default: "+0%"
          example: "+10%"
        - name: pitch
          in: query
          required: false
          description: "音调，格式如 +5Hz、-3Hz"
          schema:
            type: string
            default: "+0Hz"
          example: "+5Hz"
      responses:
        '200':
          description: MP3 音频流
          content:
            audio/mpeg:
              schema:
                type: string
                format: binary
        '400':
          description: 参数错误（如 text 为空）
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/tts/voices:
    get:
      tags: [语音]
      summary: 获取可用语音列表
      description: 返回当前配置的语音及所有可用的中文语音
      operationId: listVoices
      responses:
        '200':
          description: 语音列表
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/VoiceListResponse'
              example:
                current: "zh-CN-XiaoxiaoNeural"
                voices:
                  - name: "zh-CN-XiaoxiaoNeural"
                    gender: "Female"
                    locale: "zh-CN"
                  - name: "zh-CN-YunxiNeural"
                    gender: "Male"
                    locale: "zh-CN"
        '503':
          description: 语音列表服务不可用
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /api/models:
    get:
      tags: [模型]
      summary: 获取可用模型列表
      description: 返回当前使用的模型及 Ollama 中所有可用的模型
      operationId: listModels
      responses:
        '200':
          description: 模型列表
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ModelListResponse'
              example:
                current: "qwen2.5:7b"
                available:
                  - "qwen2.5:7b"
                  - "llama3:8b"
        '503':
          description: Ollama 服务不可用
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

components:
  schemas:
    ChatMessage:
      type: object
      description: 聊天历史消息
      required:
        - role
        - content
      properties:
        role:
          type: string
          description: 消息角色
          enum: [user, assistant, system]
          example: user
        content:
          type: string
          description: 消息内容
          example: "你好"

    ChatRequest:
      type: object
      description: 聊天请求
      required:
        - message
      properties:
        message:
          type: string
          description: 当前用户消息
          example: "请介绍一下你自己"
        history:
          type: array
          description: 对话历史（不含当前消息）
          items:
            $ref: '#/components/schemas/ChatMessage'
          example:
            - role: user
              content: "上一轮问题"
            - role: assistant
              content: "上一轮回答"

    HealthResponse:
      type: object
      description: 健康检查响应
      properties:
        status:
          type: string
          description: 服务状态
          example: ok
        model:
          type: string
          description: 当前使用的 AI 模型
          example: "qwen2.5:7b"

    VoiceInfo:
      type: object
      description: 语音信息
      properties:
        name:
          type: string
          description: 语音短名称
          example: "zh-CN-XiaoxiaoNeural"
        gender:
          type: string
          description: 性别
          example: "Female"
        locale:
          type: string
          description: 语言区域
          example: "zh-CN"

    VoiceListResponse:
      type: object
      description: 语音列表响应
      properties:
        current:
          type: string
          description: 当前使用的语音
          example: "zh-CN-XiaoxiaoNeural"
        voices:
          type: array
          description: 可用语音列表
          items:
            $ref: '#/components/schemas/VoiceInfo'

    ModelListResponse:
      type: object
      description: 模型列表响应
      properties:
        current:
          type: string
          description: 当前使用的模型
          example: "qwen2.5:7b"
        available:
          type: array
          description: 可用模型名称列表
          items:
            type: string
          example:
            - "qwen2.5:7b"
            - "llama3:8b"

    ErrorResponse:
      type: object
      description: 错误响应
      properties:
        detail:
          type: string
          description: 错误详情
          example: "Ollama 服务不可用: Connection refused"
```

---

## 四、迁移变更对照表

| 项目 | Python (当前) | Java (目标) |
|------|--------------|-------------|
| 框架 | FastAPI | Spring Boot 3.3 + WebFlux |
| AI 调用 | ollama-python SDK | Spring AI (Ollama Starter) |
| 流式聊天 | `StreamingResponse(generate())` | `Flux<String>` (SSE) |
| TTS | edge-tts Python 库 | Reactor Netty WebSocket 直连 |
| 流式音频 | `StreamingResponse(async gen)` | `Flux<byte[]>` |
| 配置 | python-dotenv + config.py | @ConfigurationProperties + application.yml |
| CORS | CORSMiddleware | WebFluxCorsFilter |
| 数据校验 | Pydantic BaseModel | Jakarta Validation |
| API 文档 | FastAPI 自带 Swagger | SpringDoc OpenAPI |
| 端口 | 8000 | 8000（不变） |

## 五、前端兼容性

迁移后前端 **无需修改**，所有接口路径、请求格式、响应格式完全一致：

- `VITE_API_BASE` 默认 `http://localhost:8000`
- `POST /api/chat` — JSON 请求 → `text/plain` 流式响应
- `GET /api/tts?text=...` → `audio/mpeg` 流式响应
- `GET /api/tts/voices` → JSON
- `GET /api/models` → JSON
- `GET /api/health` → JSON

## 六、扩展建议

| 方向 | 说明 |
|------|------|
| 多 LLM 后端 | Spring AI 支持 Ollama / OpenAI / ZhipuAI 等，通过配置切换 |
| 对话持久化 | 接入数据库（PostgreSQL / MySQL）存储聊天记录 |
| 认证鉴权 | Spring Security + JWT |
| 并发限流 | Resilience4j RateLimiter |
| 容器化 | Dockerfile + docker-compose（Ollama + Java 后端） |
| TTS 缓存 | 相同文本+参数的音频结果缓存，减少重复合成 |
