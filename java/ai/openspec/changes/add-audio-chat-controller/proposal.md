# Change: 新增 AudioChatController 音频聊天 REST 接口

## Why

当前 ai-platform 模块缺少 3D 数字人场景所需的 REST API 接口（健康检查、流式聊天、TTS 语音合成、语音列表、模型列表），前端无法通过标准 HTTP 接口调用这些能力。需要参考 `docs/spec.md` 规范，在 `AudioChatController` 中实现完整的 5 个 API 端点，与现有 WebSocket 语音对话服务互补。

## What Changes

### 新增 AudioChatController（`/api` 路径前缀）

所有业务逻辑内聚在 `AudioChatController` 中，不修改现有 Service 层代码，保证变更范围可控。

实现 `docs/spec.md` 中定义的 5 个 REST API 端点：

1. **GET `/api/health`** — 健康检查，直接调用 `OpenAiChatModel` 检测连接状态
2. **POST `/api/chat`** — 流式聊天，直接注入 `ChatClient` 流式生成，返回 `text/plain` 逐 token 流
3. **GET `/api/tts`** — 文本转语音，Controller 内部实现 Edge TTS WebSocket 连接逻辑，流式返回 `audio/mpeg`
4. **GET `/api/tts/voices`** — 获取可用语音列表，Controller 内部调用 Edge TTS 语音列表 API
5. **GET `/api/models`** — 获取可用模型列表，Controller 内部调用 Ollama REST API

### 新增 VO 对象（`domain/vo` 下）

- `VoiceInfo.java` — 语音信息（name, gender, locale）
- `VoiceListResponse.java` — 语音列表响应（current, voices）

复用现有 VO：
- `ChatRequest` — 已存在
- `HealthResponse` — 已存在
- `ModelsResponse` — 已存在

### 不修改的文件

- `TtsService.java` — 不修改，AudioChatController 内部自行实现 Edge TTS 逻辑
- `LlmService.java` — 不修改，AudioChatController 直接注入 `ChatClient`
- `application.yaml` — TTS 配置已存在，无需修改

## Impact

- Affected specs: ai-platform (新增能力)
- Affected code:
  - 新增: `AudioChatController.java`（包含全部业务逻辑）
  - 新增: `VoiceInfo.java`, `VoiceListResponse.java`
  - 不修改任何现有文件
- 变更范围: 仅新增 3 个文件，0 个文件修改
- 前端兼容: 所有 API 路径和响应格式与 `docs/spec.md` 中 OpenAPI 规范完全一致
