# Change: 支持并发 VAD 检测

## Why
当前 `VadService` 使用单一 `SlieroVadDetector` 实例，`SlieroVadOnnxModel` 内部的 `state`、`context` 等状态变量是非线程安全的实例变量，无法支持并发调用。在高并发音频检测场景下，需要支持多线程并行执行 VAD 检测以提升吞吐量。

## What Changes
- 新增 `ConcurrentVadService`，基于自定义参数线程池管理多个独立 `SlieroVadDetector` 实例
- 线程池参数通过配置文件自定义（核心线程数、最大线程数、队列容量、拒绝策略）
- 核心线程数默认值 = CPU 线程数（`Runtime.getRuntime().availableProcessors()`）
- 默认使用 `AbortPolicy` 拒绝策略，超出容量时抛出 `RejectedExecutionException`
- 每个工作线程拥有独立的 `SlieroVadDetector` 实例，避免状态竞争
- `ConcurrentVadService` 标注 `@Primary`，作为默认 VAD 服务注入
- `VoiceAsrController.recognize()` 改为使用 `ConcurrentVadService` 执行 VAD 检测
- 提供并发批量检测方法，支持 `List<byte[]>` 批量输入、并行处理、结果有序返回

## Impact
- Affected specs: ai-platform (新增并发 VAD 检测能力)
- Affected code:
  - 新增 `ai-platform/.../infra/vad/ConcurrentVadService.java`
  - 修改 `ai-platform/.../trigger/api/VoiceAsrController.java`（注入 `ConcurrentVadService`）
  - 不修改现有 `VadService`、`SlieroVadDetector`、`SlieroVadOnnxModel`
