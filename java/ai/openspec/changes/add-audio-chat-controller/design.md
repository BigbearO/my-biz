# Design: AudioChatController 音频聊天 REST 接口

## Context

### 背景
- 3D 数字人前端需要通过 REST API 实现基础交互（聊天、TTS、健康检查等）
- 现有 WebSocket 语音对话服务已实现实时语音交互，但缺少标准 HTTP 接口
- `docs/spec.md` 定义了完整的 OpenAPI 3.0 规范，包括 5 个端点

### 约束
- Java 21 + Spring Boot 3.x
- **业务逻辑全写在 AudioChatController 中**，不修改现有 Service 层，保证范围可控
- Controller 直接注入 Spring AI 的 `ChatClient` 和 `OpenAiChatModel`
- Edge TTS WebSocket 逻辑作为 Controller 的私有方法实现
- 流式响应使用 `Flux<String>`（聊天）和 `Flux<DataBuffer>`（TTS）
- 前端兼容性：API 路径、请求/响应格式与 `docs/spec.md` 完全一致

## Goals / Non-Goals

### Goals
- 实现 5 个 REST API 端点，完整覆盖 `docs/spec.md` 规范
- 所有业务逻辑内聚在 AudioChatController 中，变更范围最小化
- 流式聊天使用 `text/plain` 逐 token 返回
- TTS 使用 Edge TTS WebSocket 合成，流式返回 `audio/mpeg`
- 支持语音列表和模型列表查询
- 健康检查包含 AI 模型连接状态

### Non-Goals
- WebSocket 语音对话（已在 `add-voice-dialog` 中实现）
- 对话持久化
- 认证鉴权
- 限流
- 重构或修改现有 Service 层

## Decisions

### D1: 业务逻辑内聚在 Controller 中
- **Decision**: 所有业务逻辑写在 AudioChatController 中
- **Reason**:
  - 变更范围可控：0 个现有文件修改
  - 不影响现有 WebSocket 语音对话链路
  - 后续如果需要抽象，可从 Controller 提取到 Service
- **Alternatives**: 扩展 TtsService / 新建 AudioChatService（变更范围大，影响现有代码）

### D2: Controller 统一 vs 拆分
- **Decision**: 统一为 `AudioChatController`
- **Reason**: 5 个接口关联性强，属于同一业务域（3D 数字人），统一管理更清晰
- **Alternatives**: 拆分为 HealthController、ChatController、TtsController、ModelController

### D3: TTS 实现
- **Decision**: Controller 内部私有方法实现 Edge TTS WebSocket 连接
- **Reason**: 与现有 TtsService 解耦，避免修改已有代码
- **Alternatives**: 扩展 TtsService（需修改现有 Service，影响 WebSocket 语音对话链路）

### D4: 流式聊天响应格式
- **Decision**: `Content-Type: text/plain`，逐 token 流式返回
- **Reason**: 与 `docs/spec.md` 规范一致，前端通过 `fetch` + `ReadableStream` 读取
- **Alternatives**: SSE (`text/event-stream`)，需修改前端

### D5: 模型列表获取
- **Decision**: Controller 内部直接调用 Ollama REST API (`GET /api/tags`)
- **Reason**: 不引入新依赖，与现有架构解耦
- **Alternatives**: 使用 Spring AI Ollama API（需额外配置）

## Architecture

```
┌──────────────────┐     HTTP/REST      ┌─────────────────────────────────────────┐
│   3D 数字人前端   │────────────────────│  AudioChatController                    │
│                  │                    │  (全部业务逻辑内聚)                       │
│  - 聊天界面      │  POST /api/chat    │                                         │
│  - TTS 播放      │  GET  /api/tts     │  ├── healthCheck()    → ChatModel        │
│  - 设置面板      │  GET  /api/voices  │  ├── chat()           → ChatClient       │
│                  │  GET  /api/models  │  ├── textToSpeech()   → Edge TTS WS     │
└──────────────────┘                    │  ├── listVoices()     → Edge TTS API     │
                                        │  └── listModels()     → Ollama REST API  │
                                        └─────────────────────────────────────────┘
                                                  │ 注入依赖
                                        ┌─────────┼──────────┐
                                        ▼         ▼          ▼
                                   ChatClient  ChatModel  application.yaml
                                   (Spring AI) (Spring AI) (TTS 配置)
```

## Controller 内部方法设计

### 公开接口方法（5 个端点）

| 方法 | 路径 | 返回类型 | 依赖注入 |
|------|------|----------|----------|
| `healthCheck()` | GET /api/health | `Mono<HealthResponse>` | `OpenAiChatModel` |
| `chat()` | POST /api/chat | `Flux<String>` | `ChatClient` |
| `textToSpeech()` | GET /api/tts | `Flux<DataBuffer>` | 无（内部 Edge TTS） |
| `listVoices()` | GET /api/tts/voices | `Mono<VoiceListResponse>` | 无（内部 Edge TTS） |
| `listModels()` | GET /api/models | `Mono<ModelsResponse>` | 无（内部 Ollama API） |

### 私有业务方法

| 方法 | 功能 | 说明 |
|------|------|------|
| `connectEdgeTts(text, voice, rate, pitch)` | Edge TTS WebSocket 连接 | 使用 Reactor Netty WebSocketClient |
| `fetchVoicesFromEdgeTts()` | 获取语音列表 | HTTP 请求 Edge TTS 语音列表 API |
| `fetchModelsFromOllama()` | 获取模型列表 | HTTP 请求 Ollama REST API |
| `buildTtsConfig()` | 构建 TTS 配置 | 从 application.yaml 读取 |

### 配置属性（`@Value` 注入）

```yaml
app:
  tts:
    voice: zh-CN-XiaoxiaoNeural
    rate: "+0%"
    pitch: "+0Hz"
    connect-timeout: 5000
    read-timeout: 30000

spring:
  ai:
    openai:
      chat:
        model: qwen2.5:7b   # 当前模型名称（用于 health 和 models）
      base-url: http://localhost:11434  # Ollama 地址（用于 models 和 health）
```

## API Endpoints Detail

### 1. GET /api/health
- 尝试调用 `chatModel.call("hi")` 检测连接
- 成功: `{"status": "ok", "model": "qwen2.5:7b"}`
- 失败: HTTP 503, `{"status": "error", "detail": "Ollama 服务不可用: ..."}`

### 2. POST /api/chat
- 接收 `ChatRequest`（message + history）
- 使用 `chatClient.prompt().user(message).stream().content()` 流式生成
- `Content-Type: text/plain; charset=utf-8`

### 3. GET /api/tts
- 参数: `text`(必填), `voice`(可选), `rate`(可选), `pitch`(可选)
- 内部调用 `connectEdgeTts()` 建立 WebSocket 连接
- Edge TTS 协议: 配置请求 → SSML 发送 → MP3 分片接收
- `Content-Type: audio/mpeg`

### 4. GET /api/tts/voices
- 内部调用 `fetchVoicesFromEdgeTts()` 获取语音列表
- 返回当前配置语音 + 所有可用中文语音

### 5. GET /api/models
- 内部调用 `fetchModelsFromOllama()` 获取模型列表
- 请求 `GET {base-url}/api/tags`
- 返回当前配置模型 + 所有可用模型

## Edge TTS WebSocket 协议（Controller 内部实现）

```
1. WebSocket 连接: wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1
2. 请求头: Pragma: no-cache, X-ConnectionId: <uuid>
3. 配置消息: Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}
4. SSML 消息: X-RequestId:<uuid>\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'><voice name='<voice>'><prosody rate='<rate>' pitch='<pitch>'><text></prosody></voice></speak>
5. 接收二进制 MP3 分片，去掉 header 前缀
6. 收到 Path:turn.end 时关闭连接
```

## Risks / Trade-offs

### R1: Controller 代码量较大
- **Risk**: 5 个端点 + Edge TTS WebSocket 逻辑，Controller 代码较多
- **Mitigation**: 私有方法清晰分组，后续可提取到 Service；当前优先保证范围可控
- **Trade-off**: 可维护性 vs 变更范围可控性，当前选择后者

### R2: Edge TTS 稳定性
- **Risk**: Edge TTS 是非官方接口，可能变更
- **Mitigation**: 逻辑集中在 Controller 私有方法中，便于整体替换

### R3: 与现有 TtsService 重复
- **Risk**: Edge TTS 逻辑可能与 TtsService 未来扩展重复
- **Mitigation**: 当前 TtsService 是空实现（TODO），未来如果 TtsService 实现了 Edge TTS，可从 Controller 提取切换

## Open Questions

- Q1: Edge TTS WebSocket 连接是否需要连接池？（建议首次实现不使用，按需创建连接）
- Q2: TTS 合成结果是否需要缓存？（`docs/spec.md` 建议缓存，首次实现不缓存，后续迭代添加）
