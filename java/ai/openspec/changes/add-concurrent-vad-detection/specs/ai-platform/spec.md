## ADDED Requirements

### Requirement: 并发 VAD 检测
系统 SHALL 提供 `ConcurrentVadService`，支持多线程并行执行 VAD 检测。每个工作线程使用独立的 `SlieroVadDetector` 实例。线程池参数通过配置文件自定义：核心线程数（默认 CPU 线程数）、最大线程数、队列容量、拒绝策略（默认 AbortPolicy）。

#### Scenario: 批量并发检测
- **WHEN** 调用 `detectSpeechConcurrently(List<byte[]>)` 传入 N 条音频数据
- **THEN** 使用线程池并行检测，返回与输入顺序一致的结果列表 `List<Boolean>`

#### Scenario: 单条并发检测
- **WHEN** 调用 `detectSpeech(byte[])` 传入单条音频数据
- **THEN** 将检测任务提交到线程池异步执行，调用方通过 `Future.get()` 阻塞等待结果返回

#### Scenario: 线程池饱和拒绝
- **WHEN** 并发请求数超过线程池容量且队列已满
- **THEN** 抛出 `RejectedExecutionException`

#### Scenario: 资源释放
- **WHEN** Spring 容器销毁
- **THEN** 优雅关闭线程池，并释放所有 `SlieroVadDetector` 的 `OrtSession` 资源

#### Scenario: 自定义线程池参数
- **WHEN** 配置文件中设置了 `voice.vad.pool.core-size`、`voice.vad.pool.max-size`、`voice.vad.pool.queue-capacity`、`voice.vad.pool.rejected-policy`
- **THEN** 线程池使用配置的参数创建；未设置的参数使用默认值

### Requirement: VoiceAsrController 使用并发 VAD 检测
系统 SHALL 在 `VoiceAsrController.recognize()` 接口中使用 `ConcurrentVadService` 执行 VAD 检测，等待 VAD 检测完成后再进行 ASR 识别。

#### Scenario: 语音识别接口 VAD 检测等待 ASR
- **WHEN** 用户上传 WAV 音频文件调用 `/api/voice/asr` 接口
- **THEN** 将 VAD 检测任务提交到线程池执行，阻塞等待 VAD 检测结果返回
- **THEN** VAD 检测到语音后，再调用 ASR 服务进行语音识别；VAD 未检测到语音则直接返回空结果
