package com.bigbear.ai.platform.infra.processor;

import ai.onnxruntime.OrtException;
import com.bigbear.ai.platform.infra.vad.SlieroVadDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VAD语音活动检测处理器
 */
@Slf4j
@Component
public class VadProcessor {

    private final AudioDataHandler audioDataHandler;

    // VAD配置
    @Value("${voice.chat.vad.start-threshold:0.5}")
    private float startThreshold;

    @Value("${voice.chat.vad.end-threshold:0.2}")
    private float endThreshold;

    @Value("${voice.chat.vad.min-silence-ms:500}")
    private int minSilenceDurationMs;

    @Value("${voice.chat.vad.speech-pad-ms:400}")
    private int speechPadMs;

    @Value("${voice.chat.vad.model-path:classpath:models/silero_vad.onnx}")
    private String modelPath;

    // 采样率
    private static final int SAMPLE_RATE = 16000;

    // agentId -> VAD检测器
    private final Map<String, SlieroVadDetector> detectors = new ConcurrentHashMap<>();

    // agentId -> 累积的音频数据
    private final Map<String, byte[]> audioBuffers = new ConcurrentHashMap<>();

    public VadProcessor(AudioDataHandler audioDataHandler) {
        this.audioDataHandler = audioDataHandler;
    }

    /**
     * VAD回调
     */
    public interface VadCallback {
        void onSpeechStart();
        void onSpeechEnd(byte[] completeAudio);
    }

    /**
     * 处理音频数据
     *
     * @param opusData Opus编码的音频数据
     * @param agentId 智能体ID
     * @param callback 回调
     */
    public void process(byte[] opusData, String agentId, VadCallback callback) {
        try {
            // Opus解码
            byte[] pcmData = audioDataHandler.processOpusData(opusData);

            // 获取或创建VAD检测器
            SlieroVadDetector detector = detectors.computeIfAbsent(agentId, this::createDetector);

            // 累积音频
            byte[] buffer = audioBuffers.computeIfAbsent(agentId, k -> new byte[0]);
            byte[] newBuffer = new byte[buffer.length + pcmData.length];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            System.arraycopy(pcmData, 0, newBuffer, buffer.length, pcmData.length);
            audioBuffers.put(agentId, newBuffer);

            // VAD检测
            Map<String, Double> result = detector.apply(pcmData, true);

            if (result.containsKey("start")) {
                log.info("检测到语音开始: agentId={}, start={}", agentId, result.get("start"));
                callback.onSpeechStart();
            }

            if (result.containsKey("end")) {
                log.info("检测到语音结束: agentId={}, end={}", agentId, result.get("end"));
                byte[] completeAudio = audioBuffers.get(agentId);
                audioBuffers.put(agentId, new byte[0]);  // 清空缓冲区
                callback.onSpeechEnd(completeAudio);
            }

        } catch (Exception e) {
            log.error("VAD处理失败: agentId={}", agentId, e);
        }
    }

    /**
     * 创建VAD检测器
     */
    private SlieroVadDetector createDetector(String agentId) {
        try {
            // TODO: 加载ONNX模型
            // 这里先模拟创建一个检测器
            log.info("创建VAD检测器: agentId={}", agentId);

            // 实际使用时需要加载模型文件
            // return new SlieroVadDetector(modelPath, startThreshold, endThreshold,
            //        SAMPLE_RATE, minSilenceDurationMs, speechPadMs);

            return null;

        } catch (Exception e) {
            log.error("创建VAD检测器失败: agentId={}", agentId, e);
            return null;
        }
    }

    /**
     * 重置VAD状态
     *
     * @param agentId 智能体ID
     */
    public void reset(String agentId) {
        SlieroVadDetector detector = detectors.get(agentId);
        if (detector != null) {
            detector.reset();
        }
        audioBuffers.put(agentId, new byte[0]);
    }

    /**
     * 关闭VAD检测器
     *
     * @param agentId 智能体ID
     */
    public void close(String agentId) {
        SlieroVadDetector detector = detectors.remove(agentId);
        if (detector != null) {
            try {
                detector.close();
            } catch (OrtException e) {
                log.error("关闭VAD失败: agentId={}", agentId, e);
            }
        }
        audioBuffers.remove(agentId);
    }
}
