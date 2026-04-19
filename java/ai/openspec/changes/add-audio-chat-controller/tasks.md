# Tasks: AudioChatController 音频聊天 REST 接口

## 1. VO 对象新增

- [x] 1.1 创建 `VoiceInfo.java` — 语音信息对象（name, gender, locale）
- [x] 1.2 创建 `VoiceListResponse.java` — 语音列表响应对象（current, voices）

## 2. AudioChatController 框架搭建

- [x] 2.1 创建 `AudioChatController.java`，路径前缀 `/api`
- [x] 2.2 注入依赖: `ChatClient`、`ChatModel`、`ObjectMapper`、`@Value` 配置属性
- [x] 2.3 声明 TTS 配置属性字段（voice, rate, pitch, connect-timeout, read-timeout, base-url, model-name）

## 3. 健康检查端点

- [x] 3.1 实现 `GET /api/health` — 调用 `chatModel` 检测连接状态，成功返回 `HealthResponse`，失败返回 error

## 4. 流式聊天端点

- [x] 4.1 实现 `POST /api/chat` — 接收 `ChatRequest`，使用 `chatClient.prompt().user(message).stream().content()` 返回 `Flux<String>`
- [x] 4.2 设置 `produces = MediaType.TEXT_PLAIN_VALUE`

## 5. TTS 语音合成端点（Controller 内部私有方法实现 Edge TTS）

- [x] 5.1 实现私有方法 `connectEdgeTts(text, voice, rate, pitch)` — Reactor Netty WebSocketClient 连接 Edge TTS
- [x] 5.2 实现 Edge TTS WebSocket 协议: 配置消息发送、SSML 构造与发送、MP3 分片接收
- [x] 5.3 实现 `GET /api/tts` — 接收 text/voice/rate/pitch 参数（text 必填），调用 `connectEdgeTts()` 返回 `Flux<DataBuffer>`
- [x] 5.4 参数校验: text 为空时返回 HTTP 400

## 6. 语音列表端点（Controller 内部私有方法实现）

- [x] 6.1 实现私有方法 `fetchVoicesFromEdgeTts()` — HTTP 请求 Edge TTS 语音列表 API
- [x] 6.2 实现 `GET /api/tts/voices` — 调用 `fetchVoicesFromEdgeTts()` 返回 `VoiceListResponse`

## 7. 模型列表端点（Controller 内部私有方法实现）

- [x] 7.1 实现私有方法 `fetchModelsFromOllama()` — HTTP 请求 Ollama `GET /api/tags`
- [x] 7.2 实现 `GET /api/models` — 调用 `fetchModelsFromOllama()` 返回 `ModelsResponse`

## 8. 异常处理

- [x] 8.1 Controller 内部 try-catch 处理各端点异常（400 参数校验、服务不可用降级、500 内部错误）

## 9. 测试

- [ ] 9.1 手动测试: 健康检查端点
- [ ] 9.2 手动测试: 流式聊天端点
- [ ] 9.3 手动测试: TTS 语音合成端点
- [ ] 9.4 手动测试: 语音列表和模型列表端点
