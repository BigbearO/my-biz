package com.bigbear.ai.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语音列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceListResponse {

    /**
     * 当前使用的语音
     */
    private String current;

    /**
     * 可用语音列表
     */
    private List<VoiceInfo> voices;
}
