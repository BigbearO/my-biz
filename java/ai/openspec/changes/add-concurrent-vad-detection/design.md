## Context
`SlieroVadOnnxModel` 内部维护 `state`(float[2][batch][128])、`context`(float[][]) 等有状态的实例变量，`call()` 方法会修改这些状态。`OrtSession` 本身线程安全，但模型状态不是。因此并发方案需要隔离每个线程的检测器状态。

## Goals / Non-Goals
- Goals:
  - 支持多线程并行执行 VAD 检测，提升吞吐量
  - 每个工作线程独立拥有 `SlieroVadDetector` 实例，无状态竞争
  - 线程池参数通过配置文件自定义
  - `VoiceAsrController` 使用 `ConcurrentVadService` 替代 `VadService`
- Non-Goals:
  - 不修改现有 `SlieroVadDetector`、`SlieroVadOnnxModel` 实现
  - 不实现动态线程池调整
  - 不实现 `OrtSession` 共享优化（未来可考虑）

## Decisions
- Decision: 采用**方案 A — 每线程独立 `SlieroVadDetector` 实例**
- Alternatives considered:
  - **方案 B（共享 OrtSession + ThreadLocal 状态）**: 需要改造 `SlieroVadOnnxModel`，侵入性强
  - **对象池方案**: 复杂度更高，线程局部绑定更简单可靠

### 线程池配置
- 类型: `ThreadPoolExecutor`（自定义参数）
- 配置项:
  - `voice.vad.pool.core-size` — 核心线程数，默认 `Runtime.getRuntime().availableProcessors()`
  - `voice.vad.pool.max-size` — 最大线程数，默认同核心线程数
  - `voice.vad.pool.queue-capacity` — 队列容量，默认 `100`
  - `voice.vad.pool.rejected-policy` — 拒绝策略，默认 `Abort`
- 生命周期: Spring `@PreDestroy` 优雅关闭

### 实例管理
- 使用 `ConcurrentHashMap<Long, SlieroVadDetector>` 按 Thread ID 缓存实例
- 首次使用时懒加载创建，避免启动时预加载所有实例的开销
- 关闭时遍历并释放所有 detector 的 `OrtSession` 资源

### VoiceAsrController 集成
- `ConcurrentVadService` 标注 `@Primary`，Spring 自动注入
- `VoiceAsrController` 中 `VadService` 替换为 `ConcurrentVadService`
- 单条请求通过 `detectSpeech(byte[])` 提交到线程池执行

## Risks / Trade-offs
- **内存占用**: 每个 detector 加载独立的 ONNX 模型到内存，N 个线程 = N 份模型。以 Silero VAD 模型约 2MB 计算，CPU 8 核约 16MB，可接受
- **线程池饱和**: 使用默认 `AbortPolicy`，高并发超限时抛异常。调用方需处理 `RejectedExecutionException`
- **资源泄漏**: 必须在 `@PreDestroy` 中关闭线程池和所有 detector，否则 `OrtSession` 资源泄漏
