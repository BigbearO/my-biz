package com.bigbear.ai.platform.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.tts")
public class TtsProperties {
    private String trustedClientToken;
    private String wsUrl;
    private String voice;
    private String rate;
    private String pitch;
    private int connectTimeout;
    private int readTimeout;
    private String audioFormat;
    private List<String> models;

    public TtsProperties() {
        this.trustedClientToken = "6A5AA1D4EAFF4E9FB37E23D68491D6F0";
        this.wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1";
        this.voice = "zh-CN-XiaoxiaoNeural";
        this.rate = "+0%";
        this.pitch = "+0Hz";
        this.audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        this.connectTimeout = 5000;
        this.readTimeout = 30000;
        this.models = List.of();
    }
}