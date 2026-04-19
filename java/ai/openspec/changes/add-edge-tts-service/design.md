## Context

在 ai-platform 模块中实现完整的 Edge TTS 语音合成能力。当前 TTS 逻辑内聚在 `AudioChatController` 中，使用 `@Value` 注入配置，不利于复用和测试。需要按照 `docs/tts.md` 规范，新增独立的服务层实现，不修改现有代码。

## Goals / Non-Goals

**Goals:**
- 新增 `TtsProperties` 配置属性类，类型安全管理 TTS 配置
- 新增 `EdgeTtsService`，通过 Reactor Netty WebSocket 直连 Edge TTS，流式合成 MP3
- 新增 `TtsController`，提供 `GET /api/tts` 和 `GET /api/tts/voices` 端点
- 接口与 Python 版本完全兼容，前端无需修改

**Non-Goals:**
- 不修改现有 `AudioChatController` 和 `TtsService`
- 不支持 SSML 直接输入
- 不实现本地 TTS 模型部署
- 不实现多 TTS 引擎切换

## Decisions

### Decision 1: 使用 @ConfigurationProperties 替代 @Value
- **原因**: 类型安全、集中管理、支持 JSR-303 校验、IDE 自动补全
- **代码位置**: `com.bigbear.ai.platform.config.TtsProperties`
- **替代方案**: 继续使用 `@Value`（散落在各处，不利于维护）

### Decision 2: 使用 Reactor Netty WebSocket 直连 Edge TTS
- **原因**: 项目已引入 WebFlux + Reactor Netty，天然支持响应式 WebSocket；直接与 Edge TTS 服务通信，无需额外 SDK
- **代码位置**: `com.bigbear.ai.platform.infra.service.EdgeTtsService`
- **替代方案**: 使用 edge-tts Java SDK（引入额外依赖）、使用 JDK HttpClient（不支持响应式流）

### Decision 3: 新增独立 Controller，不改现有
- **原因**: `AudioChatController` 已有 `/api/tts` 端点映射，新增 `TtsController` 使用不同路径或共存策略
- **方案**: 新增 `TtsController` 映射到 `/api/tts/v2`，避免与现有端点冲突，后续稳定后可切换
- **替代方案**: 直接替换 `AudioChatController` 中的 TTS 逻辑（改动现有代码，违反需求约束）

### Decision 4: 新增端点路径使用 /api/tts/v2
- **原因**: `AudioChatController` 已注册 `GET /api/tts`，Spring MVC 不允许重复映射
- **方案**: 新 Controller 使用 `/api/tts/v2`，前端可逐步切换

### Decision 5: 配置前缀使用 app.tts
- **原因**: 与现有 `AudioChatController` 中 `@Value("${app.tts.*}")` 保持一致，复用已有配置结构
- **配置项**: voice, rate, pitch, connect-timeout, read-timeout, audio-format, ws-url, trusted-client-token

## API 设计

```
GET /api/tts/v2?text=你好&voice=zh-CN-XiaoxiaoNeural&rate=%2B0%25&pitch=%2B0Hz
响应: Content-Type: audio/mpeg
      流式 MP3 二进制数据

GET /api/tts/v2/voices
响应: Content-Type: application/json
      { "current": "zh-CN-XiaoxiaoNeural", "voices": [...] }
```

## 新增文件清单

```
ai-platform/src/main/java/com/bigbear/ai/platform/
├── config/
│   └── TtsProperties.java           # TTS 配置属性类
├── infra/service/
│   └── EdgeTtsService.java          # Edge TTS WebSocket 合成服务
└── trigger/api/
    └── TtsController.java           # TTS REST 接口
```

## 错误处理

| 场景 | HTTP 状态码 | 处理方式 |
|------|------------|----------|
| text 参数为空 | 400 | 返回 "text 不能为空" |
| Edge TTS WebSocket 连接失败 | 500 | 日志记录 + 返回错误信息 |
| 音频合成超时 | 504 | readTimeout 配置控制 |
| 语音列表获取失败 | 200 | 返回空列表 + 默认语音 |

## Risks / Trade-offs

- **风险**: `/api/tts/v2` 路径与现有 `/api/tts` 并存，需前端切换
  - **缓解**: 接口兼容，切换成本低
- **风险**: Edge TTS 为非官方 API，可能随时变更
  - **缓解**: 配置化 ws-url，便于快速调整

## Open Questions

- [ ] 是否需要将现有 `AudioChatController` 的 TTS 逻辑迁移到新 Service？（本期不做，后续可考虑）
- [ ] 语音列表端点是否需要缓存？（建议后续优化）
