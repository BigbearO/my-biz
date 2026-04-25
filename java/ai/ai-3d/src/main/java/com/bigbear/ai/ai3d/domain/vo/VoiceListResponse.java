package com.bigbear.ai.ai3d.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceListResponse {

    private String current;

    private List<VoiceInfo> voices;
}
