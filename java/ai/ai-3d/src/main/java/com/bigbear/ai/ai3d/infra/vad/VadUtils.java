package com.bigbear.ai.ai3d.infra.vad;

import ai.onnxruntime.OrtException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VadUtils {

    public static float[] readWavFileAsFloatArray(byte[] audioBytes) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais);

        AudioFormat format = audioStream.getFormat();

        if (format.getChannels() == 2) {
            AudioFormat monoFormat = new AudioFormat(
                format.getEncoding(),
                format.getSampleRate(),
                format.getSampleSizeInBits(),
                1,
                format.getFrameSize() / 2,
                format.getFrameRate(),
                format.isBigEndian()
            );
            audioStream = AudioSystem.getAudioInputStream(monoFormat, audioStream);
        }

        byte[] audioDataBytes = audioStream.readAllBytes();
        audioStream.close();

        float[] audioData = new float[audioDataBytes.length / 2];
        for (int i = 0; i < audioData.length; i++) {
            short sample = (short) ((audioDataBytes[i * 2] & 0xff) | (audioDataBytes[i * 2 + 1] << 8));
            audioData[i] = sample / 32768.0f;
        }

        return audioData;
    }

    public static List<Map<String, Integer>> getSpeechTimestamps(
            float[] audio,
            SlieroVadOnnxModel model,
            float threshold,
            int samplingRate,
            int minSpeechDurationMs,
            int minSilenceDurationMs,
            int speechPadMs,
            float negThreshold) throws OrtException {

        model.resetStates();

        int minSpeechSamples = samplingRate * minSpeechDurationMs / 1000;
        int speechPadSamples = samplingRate * speechPadMs / 1000;
        int minSilenceSamples = samplingRate * minSilenceDurationMs / 1000;
        int windowSizeSamples = samplingRate == 16000 ? 512 : 256;
        int audioLengthSamples = audio.length;

        List<Float> speechProbs = new ArrayList<>();
        for (int currentStart = 0; currentStart < audioLengthSamples; currentStart += windowSizeSamples) {
            float[] chunk = new float[windowSizeSamples];
            int chunkLength = Math.min(windowSizeSamples, audioLengthSamples - currentStart);
            System.arraycopy(audio, currentStart, chunk, 0, chunkLength);

            if (chunkLength < windowSizeSamples) {
                for (int i = chunkLength; i < windowSizeSamples; i++) {
                    chunk[i] = 0.0f;
                }
            }

            float speechProb = model.call(new float[][]{chunk}, samplingRate)[0];
            speechProbs.add(speechProb);
        }

        boolean triggered = false;
        List<Map<String, Integer>> speeches = new ArrayList<>();
        Map<String, Integer> currentSpeech = null;
        int tempEnd = 0;

        for (int i = 0; i < speechProbs.size(); i++) {
            float speechProb = speechProbs.get(i);

            if (speechProb >= threshold && tempEnd != 0) {
                tempEnd = 0;
            }

            if (speechProb >= threshold && !triggered) {
                triggered = true;
                currentSpeech = new HashMap<>();
                currentSpeech.put("start", windowSizeSamples * i);
                continue;
            }

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

        if (currentSpeech != null &&
                (audioLengthSamples - currentSpeech.get("start")) > minSpeechSamples) {
            currentSpeech.put("end", audioLengthSamples);
            speeches.add(currentSpeech);
        }

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
