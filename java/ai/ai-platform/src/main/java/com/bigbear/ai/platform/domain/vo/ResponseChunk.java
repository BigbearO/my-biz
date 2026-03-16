package com.bigbear.ai.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应片段 - 文字和语音的组合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseChunk {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 消息类型: stt/llm/tts/audio
     */
    private String type;
    
    /**
     * 序号（保证播放顺序）
     */
    private Integer sequence;
    
    /**
     * 文字内容
     */
    private String text;
    
    /**
     * 音频数据(OPUS)
     */
    private byte[] audioData;
    
    /**
     * 状态
     */
    private String state;
    
    /**
     * 是否最后一段
     */
    private boolean isFinal;
    
    /**
     * 是否以标点结尾
     */
    private boolean endsWithPunctuation;
}
