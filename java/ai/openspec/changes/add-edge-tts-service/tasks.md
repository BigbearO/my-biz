## 1. 实现

- [x] 1.1 创建 `TtsProperties` 配置属性类，使用 `@ConfigurationProperties(prefix = "app.tts")`，包含 trustedClientToken、wsUrl、voice、rate、pitch、connectTimeout、readTimeout、audioFormat 属性，并提供默认值
- [x] 1.2 在 `ChatConfig` 中通过 `@EnableConfigurationProperties(TtsProperties.class)` 注册 TtsProperties
- [x] 1.3 创建 `EdgeTtsService` 服务类，实现 `synthesize(String text, String voice, String rate, String pitch)` 方法，通过 Reactor Netty WebSocket 直连 Edge TTS 流式合成 MP3
- [x] 1.4 在 `EdgeTtsService` 中实现 `buildConfigXml()` 私有方法构建 SSML 配置 XML
- [x] 1.5 在 `EdgeTtsService` 中实现 `buildTextMessage()` 私有方法构建 X-TTS 文本消息
- [x] 1.6 在 `EdgeTtsService` 中实现 `escapeJson()` 私有方法进行 JSON 字符串转义
- [x] 1.7 在 `EdgeTtsService` 中实现 `blankToDefault()` 私有方法处理参数默认值回填
- [x] 1.8 在 `EdgeTtsService` 中实现 `listVoices()` 方法获取可用语音列表
- [x] 1.9 创建 `TtsController`，映射 `/api/tts/v2`，提供 `GET /api/tts/v2` 文本转语音端点
- [x] 1.10 在 `TtsController` 中添加 `GET /api/tts/v2/voices` 语音列表端点
- [x] 1.11 在 `application.yaml` 中添加 `app.tts.*` 完整配置项（ws-url、trusted-client-token、voice、rate、pitch、connect-timeout、read-timeout、audio-format）

## 2. 测试

- [ ] 2.1 编写 TtsProperties 默认值单元测试
- [ ] 2.2 编写 EdgeTtsService.synthesize() 集成测试（验证 WebSocket 连接和音频返回）
- [ ] 2.3 编写 TtsController 接口测试（验证参数校验、响应格式）

## 3. 文档

- [ ] 3.1 更新 API 文档，说明 /api/tts/v2 端点用法
- [ ] 3.2 更新 tts.md 标注 Java 实现已完成
