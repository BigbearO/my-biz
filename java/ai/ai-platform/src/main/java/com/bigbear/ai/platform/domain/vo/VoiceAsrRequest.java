package com.bigbear.ai.platform.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语音识别请求
 */
@Data
@Schema(description = "语音识别请求")
public class VoiceAsrRequest {

    @Schema(description = "base64编码的WAV音频数据")
    @NotBlank(message = "音频数据不能为空")
    private String audioBase64;

    @Schema(description = "采样率，默认16000Hz")
    private Integer sampleRate = 16000;
}