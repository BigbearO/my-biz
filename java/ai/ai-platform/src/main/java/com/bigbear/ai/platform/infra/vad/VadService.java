package com.bigbear.ai.platform.infra.vad;

import ai.onnxruntime.OrtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * VAD语音活动检测服务
 */
@Slf4j
@Service
public class VadService {

    private final SlieroVadDetector vadDetector;
    private final int samplingRate;
    private final float startThreshold;

    public VadService(
            @Value("${voice.vad.model-path}") String modelPath,
            @Value("${voice.vad.start-threshold:0.5}") float startThreshold,
            @Value("${voice.vad.end-threshold:0.2}") float endThreshold,
            @Value("${voice.vad.sampling-rate:16000}") int samplingRate,
            @Value("${voice.vad.min-silence-ms:500}") int minSilenceMs,
            @Value("${voice.vad.speech-pad-ms:400}") int speechPadMs) {
        this.samplingRate = samplingRate;
        this.startThreshold = startThreshold;
        try {
            this.vadDetector = new SlieroVadDetector(
                    modelPath, startThreshold, endThreshold,
                    samplingRate, minSilenceMs, speechPadMs);
            log.info("VAD模型加载成功，采样率: {}Hz", samplingRate);
        } catch (OrtException e) {
            throw new RuntimeException("VAD模型加载失败", e);
        }
    }

    /**
     * 检测音频是否包含有效语音
     *
     * @param audioBytes WAV格式音频数据
     * @return true表示检测到语音，false表示无语音
     */
    public boolean detectSpeech(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("音频数据为空");
            return false;
        }

        try {
            // 使用标准 AudioSystem API 解析 WAV 文件
            float[] audioData = VadUtils.readWavFileAsFloatArray(audioBytes);

            int samplesCount = audioData.length;
            log.debug("音频采样点数: {}", samplesCount);

            if (samplesCount < 512) {
                log.warn("音频数据太短(少于512采样点)，无法进行VAD检测");
                return false;
            }

            // 调用 VadUtils.getSpeechTimestamps 进行语音检测
            List<Map<String, Integer>> speechTimestamps = VadUtils.getSpeechTimestamps(
                    audioData,
                    vadDetector.getModel(),
                    startThreshold,
                    samplingRate,
                    250,    // MIN_SPEECH_DURATION_MS
                    100,    // MIN_SILENCE_DURATION_MS
                    30,     // SPEECH_PAD_MS
                    0.35f   // NEG_THRESHOLD
            );

            boolean hasSpeech = !speechTimestamps.isEmpty();
            log.info("VAD检测完成，检测到 {} 个语音段，结果: {}",
                    speechTimestamps.size(), hasSpeech ? "有语音" : "无语音");
            return hasSpeech;

        } catch (Exception e) {
            log.error("VAD检测异常", e);
            return false;
        }
    }

    /**
     * 关闭VAD检测器
     */
    public void close() {
        try {
            vadDetector.close();
        } catch (OrtException e) {
            log.error("VAD检测器关闭失败", e);
        }
    }
}
