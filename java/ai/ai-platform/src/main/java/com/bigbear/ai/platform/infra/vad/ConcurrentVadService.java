package com.bigbear.ai.platform.infra.vad;

import ai.onnxruntime.OrtException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并发 VAD 语音活动检测服务
 * <p>
 * 每个工作线程使用独立的 SlieroVadDetector 实例，避免模型内部状态竞争。
 * 线程池参数通过配置文件自定义。
 */
@Slf4j
@Primary
@Service
public class ConcurrentVadService extends VadService {

    private final ExecutorService executorService;
    private final ConcurrentHashMap<Long, SlieroVadDetector> detectorCache = new ConcurrentHashMap<>();

    private final String modelPath;
    private final float startThreshold;
    private final float endThreshold;
    private final int samplingRate;
    private final int minSilenceMs;
    private final int speechPadMs;

    public ConcurrentVadService(
            @Value("${voice.vad.model-path}") String modelPath,
            @Value("${voice.vad.start-threshold:0.5}") float startThreshold,
            @Value("${voice.vad.end-threshold:0.2}") float endThreshold,
            @Value("${voice.vad.sampling-rate:16000}") int samplingRate,
            @Value("${voice.vad.min-silence-ms:500}") int minSilenceMs,
            @Value("${voice.vad.speech-pad-ms:400}") int speechPadMs,
            @Value("${voice.vad.pool.core-size:0}") int coreSize,
            @Value("${voice.vad.pool.max-size:0}") int maxSize,
            @Value("${voice.vad.pool.queue-capacity:100}") int queueCapacity,
            @Value("${voice.vad.pool.rejected-policy:Abort}") String rejectedPolicy) {
        super(modelPath, startThreshold, endThreshold, samplingRate, minSilenceMs, speechPadMs);

        int effectiveCoreSize = coreSize > 0 ? coreSize : Runtime.getRuntime().availableProcessors();
        int effectiveMaxSize = maxSize > 0 ? maxSize : effectiveCoreSize;
        RejectedExecutionHandler handler = resolveRejectedPolicy(rejectedPolicy);

        this.executorService = new ThreadPoolExecutor(
                effectiveCoreSize,
                effectiveMaxSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "vad-pool-" + (count++));
                        t.setDaemon(true);
                        return t;
                    }
                },
                handler
        );

        this.modelPath = modelPath;
        this.startThreshold = startThreshold;
        this.endThreshold = endThreshold;
        this.samplingRate = samplingRate;
        this.minSilenceMs = minSilenceMs;
        this.speechPadMs = speechPadMs;
        log.info("并发VAD服务初始化完成，线程池核心线程数: {}, 最大线程数: {}, 队列容量: {}, 拒绝策略: {}",
                effectiveCoreSize, effectiveMaxSize, queueCapacity, handler.getClass().getSimpleName());
    }

    /**
     * 批量并发检测音频是否包含有效语音
     *
     * @param audioBytesList WAV 格式音频数据列表
     * @return 检测结果列表，顺序与输入一致，true 表示检测到语音
     */
    public List<Boolean> detectSpeechConcurrently(List<byte[]> audioBytesList) {
        if (audioBytesList == null || audioBytesList.isEmpty()) {
            return List.of();
        }

        List<Callable<Boolean>> tasks = new ArrayList<>(audioBytesList.size());
        for (byte[] audioBytes : audioBytesList) {
            tasks.add(() -> detectSpeech(audioBytes));
        }

        try {
            List<Future<Boolean>> futures = executorService.invokeAll(tasks);
            List<Boolean> results = new ArrayList<>(futures.size());
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("并发VAD检测被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("并发VAD检测执行异常", e.getCause());
        }
    }

    /**
     * 单条音频检测（提交到线程池异步执行，阻塞等待结果）
     * <p>
     * 调用方提交任务到线程池后通过 Future.get() 同步等待 VAD 检测完成，
     * 确保检测结果返回后再进行后续处理（如 ASR 识别）。
     *
     * @param audioBytes WAV 格式音频数据
     * @return true 表示检测到语音，false 表示无语音
     */
    @Override
    public boolean detectSpeech(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("音频数据为空");
            return false;
        }

        try {
            Future<Boolean> future = executorService.submit(() -> doDetectSpeech(audioBytes));
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("VAD检测被中断", e);
        } catch (ExecutionException e) {
            log.error("VAD检测执行异常", e.getCause());
            return false;
        }
    }

    /**
     * 实际执行 VAD 检测（在线程池工作线程中运行）
     */
    private boolean doDetectSpeech(byte[] audioBytes) {
        try {
            SlieroVadDetector detector = getDetector();

            float[] audioData = VadUtils.readWavFileAsFloatArray(audioBytes);

            if (audioData.length < 512) {
                log.warn("音频数据太短(少于512采样点)，无法进行VAD检测");
                return false;
            }

            List<Map<String, Integer>> speechTimestamps = VadUtils.getSpeechTimestamps(
                    audioData,
                    detector.getModel(),
                    startThreshold,
                    samplingRate,
                    250,
                    100,
                    30,
                    0.35f
            );

            return !speechTimestamps.isEmpty();
        } catch (Exception e) {
            log.error("VAD检测异常", e);
            return false;
        }
    }

    /**
     * 获取当前线程的 SlieroVadDetector 实例，不存在则懒加载创建
     */
    private SlieroVadDetector getDetector() {
        long threadId = Thread.currentThread().getId();
        return detectorCache.computeIfAbsent(threadId, id -> {
            try {
                log.debug("为线程 {} 创建新的 SlieroVadDetector 实例", id);
                return new SlieroVadDetector(modelPath, startThreshold, endThreshold,
                        samplingRate, minSilenceMs, speechPadMs);
            } catch (OrtException e) {
                throw new RuntimeException("SlieroVadDetector 创建失败", e);
            }
        });
    }

    /**
     * 关闭线程池并释放所有 SlieroVadDetector 资源
     */
    @Override
    public void close() {
        super.close();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (Map.Entry<Long, SlieroVadDetector> entry : detectorCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (OrtException e) {
                log.error("关闭线程 {} 的 SlieroVadDetector 失败", entry.getKey(), e);
            }
        }
        detectorCache.clear();
        log.info("并发VAD服务已关闭");
    }

    @PreDestroy
    public void destroy() {
        close();
    }

    /**
     * 解析拒绝策略配置
     */
    private static RejectedExecutionHandler resolveRejectedPolicy(String policy) {
        return switch (policy.trim().toLowerCase()) {
            case "caller" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}
