package com.bigbear.ai.ai3d.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceAsrResponse {

    private String text;

    private Boolean hasSpeech;
}
