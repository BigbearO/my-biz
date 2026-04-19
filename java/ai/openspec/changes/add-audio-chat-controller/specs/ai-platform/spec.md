## ADDED Requirements

### Requirement: AudioChatController 音频聊天 REST 接口
系统 SHALL 提供 5 个 REST API 端点，所有业务逻辑内聚在 AudioChatController 中，不修改现有 Service 层代码。

#### Scenario: 健康检查成功
- **WHEN** 前端发送 `GET /api/health` 请求
- **THEN** Controller 直接调用 `OpenAiChatModel` 检测连接状态，返回 `{"status": "ok", "model": "<当前模型>"}`

#### Scenario: 健康检查失败
- **WHEN** AI 模型服务不可用
- **THEN** Controller 捕获异常，返回 HTTP 503，响应体 `{"status": "error", "detail": "Ollama 服务不可用: <error>"}`

#### Scenario: 流式聊天
- **WHEN** 前端发送 `POST /api/chat`，请求体为 `{"message": "你好", "history": [...]}`
- **THEN** Controller 直接使用注入的 `ChatClient` 流式生成回复，`Content-Type: text/plain; charset=utf-8`，逐 token 流式返回

#### Scenario: 文本转语音
- **WHEN** 前端发送 `GET /api/tts?text=你好&voice=zh-CN-XiaoxiaoNeural&rate=+0%&pitch=+0Hz`
- **THEN** Controller 内部通过私有方法建立 Edge TTS WebSocket 连接，合成语音并流式返回 `audio/mpeg` MP3 音频

#### Scenario: 文本转语音参数缺失
- **WHEN** 前端发送 `GET /api/tts` 但未提供 `text` 参数
- **THEN** Controller 返回 HTTP 400 错误

#### Scenario: 获取语音列表
- **WHEN** 前端发送 `GET /api/tts/voices`
- **THEN** Controller 内部通过私有方法调用 Edge TTS 语音列表 API，返回 `{"current": "<当前语音>", "voices": [{"name": "...", "gender": "...", "locale": "..."}]}`

#### Scenario: 获取模型列表
- **WHEN** 前端发送 `GET /api/models`
- **THEN** Controller 内部通过私有方法调用 Ollama REST API，返回 `{"current": "<当前模型>", "available": ["model1", "model2"]}`

### Requirement: AudioChatController Edge TTS 内部实现
系统 SHALL 在 AudioChatController 内部通过私有方法实现 Edge TTS WebSocket 连接和语音列表获取，不修改现有 TtsService。

#### Scenario: Edge TTS WebSocket 合成
- **WHEN** Controller 内部调用 `connectEdgeTts(text, voice, rate, pitch)` 私有方法
- **THEN** 使用 Reactor Netty WebSocketClient 连接微软 Edge TTS 接口，发送配置和 SSML，接收并流式返回 MP3 音频数据

#### Scenario: 语音列表获取
- **WHEN** Controller 内部调用 `fetchVoicesFromEdgeTts()` 私有方法
- **THEN** 通过 HTTP 请求 Edge TTS 语音列表 API，解析返回所有可用中文语音信息

#### Scenario: TTS 参数默认值
- **WHEN** 未指定 voice、rate、pitch 参数
- **THEN** Controller 使用 `@Value` 从配置文件读取默认值（voice=zh-CN-XiaoxiaoNeural, rate=+0%, pitch=+0Hz）

### Requirement: AudioChatController Ollama 模型列表内部实现
系统 SHALL 在 AudioChatController 内部通过私有方法调用 Ollama REST API 获取模型列表，不引入新 Service 依赖。

#### Scenario: 获取 Ollama 模型列表
- **WHEN** Controller 内部调用 `fetchModelsFromOllama()` 私有方法
- **THEN** 通过 HTTP 请求 `{base-url}/api/tags` 获取模型列表，解析返回所有可用模型名称

## MODIFIED Requirements

（无修改现有需求）

## REMOVED Requirements

（无删除需求）
