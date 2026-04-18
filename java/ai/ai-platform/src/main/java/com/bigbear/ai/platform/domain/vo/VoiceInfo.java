package com.bigbear.ai.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInfo {

    /**
     * 语音短名称
     */
    private String name;

    /**
     * 性别
     */
    private String gender;

    /**
     * 语言区域
     */
    private String locale;
}
