package com.bigbear.ai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TTS 语音合成配置属性类
 * <p>
 * 使用 @ConfigurationProperties 替代 @Value，类型安全管理 TTS 相关配置。
 * 对应 docs/tts.md 2.5 节。
 */
@Validated
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
    String trustedClientToken,
    String wsUrl,
    String voice,
    String rate,
    String pitch,
    int connectTimeout,
    int readTimeout,
    String audioFormat
) {
    public TtsProperties {
        if (trustedClientToken == null || trustedClientToken.isBlank())
            trustedClientToken = "6A5AA1D4EAFF4E9FB37E23D68491D6F0";
        if (wsUrl == null || wsUrl.isBlank())
            wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1";
        if (voice == null || voice.isBlank()) voice = "zh-CN-XiaoxiaoNeural";
        if (rate == null || rate.isBlank()) rate = "+0%";
        if (pitch == null || pitch.isBlank()) pitch = "+0Hz";
        if (audioFormat == null || audioFormat.isBlank()) audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        if (connectTimeout <= 0) connectTimeout = 5000;
        if (readTimeout <= 0) readTimeout = 30000;
    }
}
