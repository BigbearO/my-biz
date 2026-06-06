package com.bigbear.ai.ai3d.infra.service;

import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TtsService {

    @Value("${app.tts.voice:zh-CN-YunjianNeural}")
    private String defaultVoice;

    @Value("${app.tts.rate:+0%}")
    private String defaultRate;

    @Value("${app.tts.pitch:+0Hz}")
    private String defaultPitch;

    /**
     * 将文本合成为音频，返回 base64 编码的 MP3
     *
     * @param rawText 原始文本（可能包含 @@OPTIONS@@ 等标记）
     * @return base64 编码的 MP3 音频，合成失败返回 null
     */
    public String synthesizeToBase64(String rawText) {
        try {
            String cleanedText = cleanForTts(rawText);
            if (cleanedText.isBlank()) {
                return null;
            }

            Optional<Voice> voiceOptional = TTSVoice.provides()
                .stream()
                .filter(v -> defaultVoice.equals(v.getShortName()))
                .findFirst();

            if (voiceOptional.isEmpty()) {
                log.warn("TTS voice not found: {}", defaultVoice);
                return null;
            }

            TTS tts = new TTS(voiceOptional.get(), cleanedText.strip())
                .findHeadHook()
                .isRateLimited(true)
                .formatMp3()
                .voiceRate(defaultRate)
                .voicePitch(defaultPitch);

            ByteArrayOutputStream audioStream = tts.transToAudioStream();
            byte[] audioBytes = audioStream.toByteArray();

            log.info("TTS 合成完成: {} bytes, text={}", audioBytes.length,
                cleanedText.substring(0, Math.min(30, cleanedText.length())));

            return Base64.getEncoder().encodeToString(audioBytes);
        } catch (Exception e) {
            log.error("TTS 合成失败", e);
            return null;
        }
    }

    /**
     * 清理文本用于 TTS 合成，移除自定义标记和特殊符号
     */
    private String cleanForTts(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 移除自定义标记
        String cleaned = text
            .replaceAll("@@OPTIONS@@[\\s\\S]*?(@@PHASE@@|$)", "")
            .replaceAll("@@PHASE@@\\w+@@", "")
            .replaceAll("@@COMPLETED@@", "")
            .replaceAll("@@SUMMARY@@", "");

        // 移除 emoji 和特殊符号
        cleaned = cleaned
            .replaceAll("[✅✨❌❤️⭐🌟💡🔥💯👍👎🎉🎊]", "")
            .replaceAll("[\\u2460-\\u24FF]", "")
            .replaceAll("[0-9]\\uFE0F\\u20E3", "")
            .replaceAll("[\\u25A0-\\u25FF\\u2B00-\\u2BFF]", "")
            .replaceAll("[\\u2700-\\u27BF]", "")
            .replaceAll("[\\u1F680-\\u1F6FF]", "")
            .replaceAll("[\\u2600-\\u26FF]", "")
            .replaceAll("[\\u1F300-\\u1F5FF]", "")
            .replaceAll("[\\u1F600-\\u1F64F]", "")
            .replaceAll("[\\u1F900-\\u1F9FF]", "")
            .replaceAll("\\*\\*", "")
            .replaceAll("\\*", "")
            .replaceAll("`", "")
            .replaceAll("#", "")
            .replaceAll("[\\u2022\\u25CF\\u25CB\\u25A0\\u25A1\\u25AA\\uFE0F\\u25AB]\\s*", "")
            .replaceAll("\\s+", " ")
            .replaceAll("<[^>]+>", "");

        return cleaned.trim();
    }
}
