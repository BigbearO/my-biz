## 1. Implementation

- [x] 1.1 创建 `ConcurrentVadService` 类，注入 VAD 模型配置参数
- [x] 1.2 使用 `ThreadPoolExecutor` 初始化自定义参数线程池（核心/最大线程数、队列容量、拒绝策略均可配置）
- [x] 1.3 实现 `Callable<Boolean>` 内部任务，每个线程绑定独立 `SlieroVadDetector` 实例（通过 `ConcurrentHashMap<Long, Detector>` 懒加载管理）
- [x] 1.4 实现 `detectSpeechConcurrently(List<byte[]>)` 批量并发检测方法，使用 `ExecutorService.invokeAll()` 保证结果有序
- [x] 1.5 实现 `detectSpeech(byte[])` 单条检测方法，提交到线程池异步执行，通过 `Future.get()` 阻塞等待结果
- [x] 1.6 实现 `close()` 方法，优雅关闭线程池并释放所有 `SlieroVadDetector` 资源
- [x] 1.7 添加 `@PreDestroy` 生命周期回调，确保 Spring 容器销毁时清理资源
- [x] 1.8 标注 `@Primary` 使其作为默认 `VadService` 注入
- [x] 1.9 修改 `VoiceAsrController`，将 `VadService` 替换为 `ConcurrentVadService`，等待 VAD 检测完成后再进行 ASR

## 2. Testing

- [x] 2.1 编写单元测试：验证并发检测结果的正确性（与单线程 `VadService` 结果一致）
- [x] 2.2 编写并发测试：多线程同时调用 `detectSpeechConcurrently`，验证无状态混乱
- [x] 2.3 编写拒绝策略测试：线程池满载时验证抛出 `RejectedExecutionException`
