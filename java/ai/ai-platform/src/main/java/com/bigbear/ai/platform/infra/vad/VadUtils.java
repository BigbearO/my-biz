package com.bigbear.ai.platform.infra.vad;

import ai.onnxruntime.OrtException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VAD 工具类
 * 提供 WAV 文件解析和语音时间戳检测功能
 */
public class VadUtils {

    /**
     * 读取 WAV 文件并返回 float 数组
     *
     * @param audioBytes WAV 文件字节数据
     * @return 归一化到 -1.0 ~ 1.0 的音频数据
     */
    public static float[] readWavFileAsFloatArray(byte[] audioBytes) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais);

        // 获取音频格式信息
        AudioFormat format = audioStream.getFormat();
        System.out.println("Audio format: " + format);

        // 如果是立体声，转换为单声道
        if (format.getChannels() == 2) {
            AudioFormat monoFormat = new AudioFormat(
                format.getEncoding(),
                format.getSampleRate(),
                format.getSampleSizeInBits(),
                1,  // 单声道
                format.getFrameSize() / 2,  // 每帧字节数减半
                format.getFrameRate(),
                format.isBigEndian()
            );
            audioStream = AudioSystem.getAudioInputStream(monoFormat, audioStream);
            System.out.println("Converted stereo to mono for VAD processing");
        }

        // 读取所有音频数据
        byte[] audioDataBytes = audioStream.readAllBytes();
        audioStream.close();

        // 转换为 float 数组 (16-bit PCM)
        float[] audioData = new float[audioDataBytes.length / 2];
        for (int i = 0; i < audioData.length; i++) {
            short sample = (short) ((audioDataBytes[i * 2] & 0xff) | (audioDataBytes[i * 2 + 1] << 8));
            audioData[i] = sample / 32768.0f;
        }

        return audioData;
    }

    /**
     * 获取语音时间戳列表
     *
     * @param audio                音频数据 (float 数组)
     * @param model                ONNX 模型
     * @param threshold            语音阈值
     * @param samplingRate         采样率
     * @param minSpeechDurationMs  最小语音时长 (毫秒)
     * @param minSilenceDurationMs 最小静音时长 (毫秒)
     * @param speechPadMs          语音填充 (毫秒)
     * @param negThreshold          负阈值 (用于判断语音结束)
     * @return 语音时间段列表
     */
    public static List<Map<String, Integer>> getSpeechTimestamps(
            float[] audio,
            SlieroVadOnnxModel model,
            float threshold,
            int samplingRate,
            int minSpeechDurationMs,
            int minSilenceDurationMs,
            int speechPadMs,
            float negThreshold) throws OrtException {

        // 重置模型状态
        model.resetStates();

        // 计算参数
        int minSpeechSamples = samplingRate * minSpeechDurationMs / 1000;
        int speechPadSamples = samplingRate * speechPadMs / 1000;
        int minSilenceSamples = samplingRate * minSilenceDurationMs / 1000;
        int windowSizeSamples = samplingRate == 16000 ? 512 : 256;
        int audioLengthSamples = audio.length;

        // 计算所有音频分块的语音概率
        List<Float> speechProbs = new ArrayList<>();
        for (int currentStart = 0; currentStart < audioLengthSamples; currentStart += windowSizeSamples) {
            float[] chunk = new float[windowSizeSamples];
            int chunkLength = Math.min(windowSizeSamples, audioLengthSamples - currentStart);
            System.arraycopy(audio, currentStart, chunk, 0, chunkLength);

            // 不足 window size 的补零
            if (chunkLength < windowSizeSamples) {
                for (int i = chunkLength; i < windowSizeSamples; i++) {
                    chunk[i] = 0.0f;
                }
            }

            float speechProb = model.call(new float[][]{chunk}, samplingRate)[0];
            System.out.println("Chunk " + currentStart+ ", speechProb: " + speechProb);  // 添加这行
            speechProbs.add(speechProb);
        }

        // 检测语音段 (与 Python 算法一致)
        boolean triggered = false;
        List<Map<String, Integer>> speeches = new ArrayList<>();
        Map<String, Integer> currentSpeech = null;
        int tempEnd = 0;

        for (int i = 0; i < speechProbs.size(); i++) {
            float speechProb = speechProbs.get(i);

            // 如果语音概率超过阈值，重置临时结束位置
            if (speechProb >= threshold && tempEnd != 0) {
                tempEnd = 0;
            }

            // 检测语音开始
            if (speechProb >= threshold && !triggered) {
                triggered = true;
                currentSpeech = new HashMap<>();
                currentSpeech.put("start", windowSizeSamples * i);
                continue;
            }

            // 检测语音结束
            if (speechProb < negThreshold && triggered) {
                if (tempEnd == 0) {
                    tempEnd = windowSizeSamples * i;
                }
                if (windowSizeSamples * i - tempEnd < minSilenceSamples) {
                    continue;
                } else {
                    currentSpeech.put("end", tempEnd);
                    if (currentSpeech.get("end") - currentSpeech.get("start") > minSpeechSamples) {
                        speeches.add(currentSpeech);
                    }
                    currentSpeech = null;
                    tempEnd = 0;
                    triggered = false;
                }
            }
        }

        // 处理最后一段语音
        if (currentSpeech != null &&
                (audioLengthSamples - currentSpeech.get("start")) > minSpeechSamples) {
            currentSpeech.put("end", audioLengthSamples);
            speeches.add(currentSpeech);
        }

        // 添加语音填充 (与 Python 算法一致)
        for (int i = 0; i < speeches.size(); i++) {
            Map<String, Integer> speech = speeches.get(i);
            if (i == 0) {
                speech.put("start", Math.max(0, speech.get("start") - speechPadSamples));
            }
            if (i != speeches.size() - 1) {
                int silenceDuration = speeches.get(i + 1).get("start") - speech.get("end");
                if (silenceDuration < 2 * speechPadSamples) {
                    speech.put("end", speech.get("end") + silenceDuration / 2);
                    speeches.get(i + 1).put("start",
                            Math.max(0, speeches.get(i + 1).get("start") - silenceDuration / 2));
                } else {
                    speech.put("end", Math.min(audioLengthSamples, speech.get("end") + speechPadSamples));
                    speeches.get(i + 1).put("start",
                            Math.max(0, speeches.get(i + 1).get("start") - speechPadSamples));
                }
            } else {
                speech.put("end", Math.min(audioLengthSamples, speech.get("end") + speechPadSamples));
            }
        }

        return speeches;
    }
}
