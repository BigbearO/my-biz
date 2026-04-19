# Change: 新增 Edge TTS 语音合成接口与服务

## Why
ai-platform 现有的 TTS 能力内聚在 `AudioChatController` 中，缺乏独立的服务层抽象。需要按照 `docs/tts.md` 规范，新增独立的 TTS 配置属性类、Edge TTS 服务和 REST 接口，提供类型安全的配置管理、流式 MP3 音频合成、语音列表查询等完整 TTS 能力。

## What Changes
- 新增 `TtsProperties` 配置属性类，使用 `@ConfigurationProperties` 替代 `@Value`，类型安全管理 TTS 配置
- 新增 `EdgeTtsService` 服务类，通过 Reactor Netty WebSocket 直连微软 Edge TTS，实现流式音频合成
- 新增 `TtsController` REST 接口，提供 `GET /api/tts` 和 `GET /api/tts/voices` 端点
- 新增 `application.yml` 中 `app.tts.*` 配置项

## Impact
- 新增能力: ai-platform TTS 语音合成能力（配置化、服务化）
- 受影响代码: ai-platform 模块新增 config、service、controller
- 不修改现有代码: 不改动 `AudioChatController`、现有 `TtsService` 等已有类
