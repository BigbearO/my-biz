package com.bigbear.ai.platform.infra.vad;

import ai.onnxruntime.OrtException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Silero VAD Detector
 * Real-time voice activity detection
 */
public class SlieroVadDetector {
    private final SlieroVadOnnxModel model;
    private final float startThreshold;
    private final float endThreshold;
    private final int samplingRate;
    private final float minSilenceSamples;
    private final float speechPadSamples;
    private boolean triggered;
    private int tempEnd;
    private int currentSample;

    public SlieroVadDetector(String modelPath,
                             float startThreshold,
                             float endThreshold,
                             int samplingRate,
                             int minSilenceDurationMs,
                             int speechPadMs) throws OrtException {
        if (samplingRate != 8000 && samplingRate != 16000) {
            throw new IllegalArgumentException("Does not support sampling rates other than [8000, 16000]");
        }

        this.model = new SlieroVadOnnxModel(modelPath);
        this.startThreshold = startThreshold;
        this.endThreshold = endThreshold;
        this.samplingRate = samplingRate;
        this.minSilenceSamples = samplingRate * minSilenceDurationMs / 1000f;
        this.speechPadSamples = samplingRate * speechPadMs / 1000f;
        reset();
    }

    public void reset() {
        model.resetStates();
        triggered = false;
        tempEnd = 0;
        currentSample = 0;
    }

    public Map<String, Double> apply(byte[] data, boolean returnSeconds) {
        float[] audioData = new float[data.length / 2];
        for (int i = 0; i < audioData.length; i++) {
            audioData[i] = ((data[i * 2] & 0xff) | (data[i * 2 + 1] << 8)) / 32767.0f;
        }

        int windowSizeSamples = audioData.length;
        currentSample += windowSizeSamples;

        float speechProb = 0;
        try {
            speechProb = model.call(new float[][]{audioData}, samplingRate)[0];
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        if (speechProb >= startThreshold && tempEnd != 0) {
            tempEnd = 0;
        }

        if (speechProb >= startThreshold && !triggered) {
            triggered = true;
            int speechStart = (int) (currentSample - speechPadSamples);
            speechStart = Math.max(speechStart, 0);
            Map<String, Double> result = new HashMap<>();
            if (returnSeconds) {
                double speechStartSeconds = speechStart / (double) samplingRate;
                double roundedSpeechStart = BigDecimal.valueOf(speechStartSeconds).setScale(1, RoundingMode.HALF_UP).doubleValue();
                result.put("start", roundedSpeechStart);
            } else {
                result.put("start", (double) speechStart);
            }

            return result;
        }

        if (speechProb < endThreshold && triggered) {
            if (tempEnd == 0) {
                tempEnd = currentSample;
            }
            if (currentSample - tempEnd < minSilenceSamples) {
                return Collections.emptyMap();
            } else {
                int speechEnd = (int) (tempEnd + speechPadSamples);
                tempEnd = 0;
                triggered = false;
                Map<String, Double> result = new HashMap<>();

                if (returnSeconds) {
                    double speechEndSeconds = speechEnd / (double) samplingRate;
                    double roundedSpeechEnd = BigDecimal.valueOf(speechEndSeconds).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    result.put("end", roundedSpeechEnd);
                } else {
                    result.put("end", (double) speechEnd);
                }
                return result;
            }
        }

        return Collections.emptyMap();
    }

    public void close() throws OrtException {
        reset();
        model.close();
    }

    public SlieroVadOnnxModel getModel() {
        return model;
    }
}
