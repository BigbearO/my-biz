package com.bigbear.ai.platform.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音识别响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "语音识别响应")
public class VoiceAsrResponse {

    @Schema(description = "识别到的文字内容")
    private String text;

    @Schema(description = "VAD检测结果，是否包含有效语音")
    private Boolean hasSpeech;
}