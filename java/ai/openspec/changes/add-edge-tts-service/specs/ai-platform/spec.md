## ADDED Requirements

### Requirement: TTS 配置属性管理
系统SHALL通过 `TtsProperties` 配置属性类管理 TTS 相关配置，使用 `@ConfigurationProperties(prefix = "app.tts")` 替代 `@Value` 注入。

#### Scenario: 使用默认配置
- **GIVEN** application.yml 中未配置 app.tts.voice
- **WHEN** 应用启动时加载 TtsProperties
- **THEN** voice 默认值为 "zh-CN-XiaoxiaoNeural"
- **AND** rate 默认值为 "+0%"
- **AND** pitch 默认值为 "+0Hz"
- **AND** audioFormat 默认值为 "audio-24khz-48kbitrate-mono-mp3"
- **AND** connectTimeout 默认值为 5000
- **AND** readTimeout 默认值为 30000

#### Scenario: 自定义配置覆盖默认值
- **GIVEN** application.yml 中配置了 app.tts.voice=zh-CN-YunxiNeural
- **WHEN** 应用启动时加载 TtsProperties
- **THEN** voice 值为 "zh-CN-YunxiNeural"
- **AND** 其他未配置的属性使用默认值

### Requirement: Edge TTS 流式语音合成服务
系统SHALL提供 `EdgeTtsService` 服务，通过 Reactor Netty WebSocket 直连微软 Edge TTS 服务，流式合成 MP3 音频。

#### Scenario: 成功合成语音
- **GIVEN** 有效的文本输入和 Edge TTS WebSocket 可达
- **WHEN** 调用 EdgeTtsService.synthesize(text, voice, rate, pitch)
- **THEN** 建立 WebSocket 连接到 wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1
- **AND** 发送 SSML 配置 XML 和 X-TTS 文本消息
- **AND** 返回 Flux<byte[]> 流式 MP3 音频数据

#### Scenario: voice/rate/pitch 参数为空时使用默认值
- **GIVEN** 调用 synthesize 时 voice 参数为 null 或空白
- **WHEN** 执行合成
- **THEN** 使用 TtsProperties 中配置的默认 voice 值
- **AND** rate 和 pitch 同理回填默认值

#### Scenario: WebSocket 连接异常
- **GIVEN** Edge TTS 服务不可达
- **WHEN** 调用 EdgeTtsService.synthesize()
- **THEN** 记录错误日志
- **AND** 返回 Flux.error 包含 "Edge TTS 合成失败" 信息

### Requirement: TTS REST 接口
系统SHALL提供 `TtsController` REST 接口，映射到 `/api/tts/v2`，支持文本转语音和语音列表查询。

#### Scenario: 文本转语音成功
- **GIVEN** 前端发送 GET /api/tts/v2?text=你好
- **WHEN** text 参数非空
- **THEN** 返回 HTTP 200，Content-Type 为 audio/mpeg
- **AND** 响应体为流式 MP3 二进制数据
- **AND** Content-Disposition 为 inline

#### Scenario: text 参数为空
- **GIVEN** 前端发送 GET /api/tts/v2（不带 text 参数）
- **WHEN** text 参数为 null 或空白
- **THEN** 返回 HTTP 400 错误，提示 "text 不能为空"

#### Scenario: 带可选参数合成语音
- **GIVEN** 前端发送 GET /api/tts/v2?text=你好&voice=zh-CN-YunxiNeural&rate=%2B10%25&pitch=%2B5Hz
- **WHEN** 所有参数有效
- **THEN** 使用指定 voice、rate、pitch 进行合成
- **AND** 返回流式 MP3 音频数据

#### Scenario: 获取语音列表
- **GIVEN** 前端发送 GET /api/tts/v2/voices
- **WHEN** Edge TTS 语音列表 API 可达
- **THEN** 返回中文语音列表和当前默认语音

#### Scenario: 语音列表获取失败
- **GIVEN** Edge TTS 语音列表 API 不可达
- **WHEN** 请求语音列表
- **THEN** 返回空列表和默认语音名称
