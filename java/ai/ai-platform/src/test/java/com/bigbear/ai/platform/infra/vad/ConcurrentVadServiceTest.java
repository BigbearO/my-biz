//package com.bigbear.ai.platform.infra.vad;
//
//
//
//import java.io.File;
//import java.nio.file.Path;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.RejectedExecutionException;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//
///**
// * ConcurrentVadService 并发测试
// * <p>
// * 需要 Silero VAD ONNX 模型文件路径通过系统属性 vad.model.path 指定。
// * 未指定模型路径时自动跳过所有测试。
// */
//class ConcurrentVadServiceTest {
//
//    private static final String MODEL_PATH = System.getProperty("vad.model.path");
//
//    private ConcurrentVadService service;
//
//    @BeforeAll
//    static void checkModelAvailable() {
//        Assumptions.assumeTrue(MODEL_PATH != null && new File(MODEL_PATH).exists(),
//                "跳过测试：未指定 -Dvad.model.path 系统属性或模型文件不存在");
//    }
//
//    @BeforeEach
//    void setUp() {
//        service = new ConcurrentVadService(
//                MODEL_PATH, 0.5f, 0.2f, 16000, 500, 400,
//                2, 2, 10, "Abort");
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (service != null) {
//            service.close();
//        }
//    }
//
//    /**
//     * 2.1 验证并发检测结果的正确性：与单线程 VadService 结果一致
//     */
//    @Test
//    void detectSpeech_resultConsistentWithSingleThread() {
//        VadService singleThreadService = new VadService(
//                MODEL_PATH, 0.5f, 0.2f, 16000, 500, 400);
//
//        try {
//            byte[] audioData = generateTestWav(16000, 1.0f);
//            boolean singleResult = singleThreadService.detectSpeech(audioData);
//
//            // 并发检测同一份数据
//            List<Boolean> concurrentResults = service.detectSpeechConcurrently(
//                    Collections.nCopies(3, audioData));
//
//            assertEquals(3, concurrentResults.size());
//            for (Boolean result : concurrentResults) {
//                assertEquals(singleResult, result, "并发检测结果应与单线程一致");
//            }
//        } finally {
//            singleThreadService.close();
//        }
//    }
//
//    /**
//     * 2.2 多线程同时调用 detectSpeechConcurrently，验证无状态混乱
//     */
//    @Test
//    void detectSpeechConcurrently_noStateCorruptionUnderConcurrency() throws Exception {
//        byte[] audioData = generateTestWav(16000, 1.0f);
//        int threadCount = 8;
//        int batchSize = 5;
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch doneLatch = new CountDownLatch(threadCount);
//        AtomicInteger successCount = new AtomicInteger(0);
//        AtomicInteger errorCount = new AtomicInteger(0);
//
//        for (int t = 0; t < threadCount; t++) {
//            new Thread(() -> {
//                try {
//                    startLatch.await();
//                    List<byte[]> batch = Collections.nCopies(batchSize, audioData);
//                    List<Boolean> results = service.detectSpeechConcurrently(batch);
//                    assertEquals(batchSize, results.size());
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    errorCount.incrementAndGet();
//                    e.printStackTrace();
//                } finally {
//                    doneLatch.countDown();
//                }
//            }).start();
//        }
//
//        startLatch.countDown();
//        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在超时内完成");
//        assertEquals(threadCount, successCount.get(), "所有线程应成功完成");
//        assertEquals(0, errorCount.get(), "不应有异常发生");
//    }
//
//    /**
//     * 2.3 线程池满载时验证抛出 RejectedExecutionException
//     */
//    @Test
//    void detectSpeech_rejectedWhenPoolFull() throws Exception {
//        // 创建一个只有1个线程、队列容量为1的 service，更容易触发拒绝
//        ConcurrentVadService smallPoolService = new ConcurrentVadService(
//                MODEL_PATH, 0.5f, 0.2f, 16000, 500, 400,
//                1, 1, 1, "Abort");
//
//        try {
//            byte[] audioData = generateTestWav(16000, 1.0f);
//
//            // 用一个阻塞任务占住线程池唯一的线程
//            CountDownLatch blockingLatch = new CountDownLatch(1);
//            smallPoolService.getExecutorService().submit(() -> {
//                try {
//                    blockingLatch.await(10, TimeUnit.SECONDS);
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
//            });
//
//            // 等待任务启动
//            Thread.sleep(200);
//
//            // 再提交一个任务填满队列（容量=1）
//            smallPoolService.getExecutorService().submit(() -> {});
//
//            Thread.sleep(100);
//
//            // 第三个任务应该被拒绝（AbortPolicy）
//            assertThrows(RejectedExecutionException.class, () -> {
//                smallPoolService.getExecutorService().submit(() -> {});
//            }, "线程池和队列满时应抛出 RejectedExecutionException");
//
//            blockingLatch.countDown();
//        } finally {
//            smallPoolService.close();
//        }
//    }
//
//    /**
//     * 空数据边界测试
//     */
//    @Test
//    void detectSpeech_emptyInput() {
//        assertFalse(service.detectSpeech(null));
//        assertFalse(service.detectSpeech(new byte[0]));
//    }
//
//    /**
//     * 批量空列表测试
//     */
//    @Test
//    void detectSpeechConcurrently_emptyList() {
//        List<Boolean> result = service.detectSpeechConcurrently(List.of());
//        assertTrue(result.isEmpty());
//    }
//
//    /**
//     * 生成测试用 WAV 音频数据（16kHz, 16bit, 单声道, 正弦波）
//     */
//    private static byte[] generateTestWav(int sampleRate, float durationSeconds) {
//        int numSamples = (int) (sampleRate * durationSeconds);
//        int dataSize = numSamples * 2;
//        int fileSize = 44 + dataSize;
//
//        byte[] wav = new byte[fileSize];
//
//        // RIFF header
//        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
//        writeInt32(wav, 4, fileSize - 8);
//        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
//
//        // fmt sub-chunk
//        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
//        writeInt32(wav, 16, 16);
//        writeInt16(wav, 20, (short) 1);
//        writeInt16(wav, 22, (short) 1);
//        writeInt32(wav, 24, sampleRate);
//        writeInt32(wav, 28, sampleRate * 2);
//        writeInt16(wav, 32, (short) 2);
//        writeInt16(wav, 34, (short) 16);
//
//        // data sub-chunk
//        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
//        writeInt32(wav, 40, dataSize);
//
//        // 440Hz 正弦波
//        for (int i = 0; i < numSamples; i++) {
//            double t = (double) i / sampleRate;
//            short sample = (short) (Short.MAX_VALUE * 0.5 * Math.sin(2 * Math.PI * 440 * t));
//            wav[44 + i * 2] = (byte) (sample & 0xff);
//            wav[44 + i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
//        }
//
//        return wav;
//    }
//
//    private static void writeInt32(byte[] buf, int offset, int value) {
//        buf[offset] = (byte) (value & 0xff);
//        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
//        buf[offset + 2] = (byte) ((value >> 16) & 0xff);
//        buf[offset + 3] = (byte) ((value >> 24) & 0xff);
//    }
//
//    private static void writeInt16(byte[] buf, int offset, short value) {
//        buf[offset] = (byte) (value & 0xff);
//        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
//    }
//}
