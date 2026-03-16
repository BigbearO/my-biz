package com.bigbear.ai.platform.domain.vo;

import lombok.Data;

/**
 * 语音对话消息
 */
@Data
public class VoiceChatMessage {
    
    /**
     * 消息类型: hello/listen/abort/stt/llm/tts/audio
     */
    private String type;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 设备ID
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 设备MAC地址
     */
    private String deviceMac;
    
    /**
     * 认证令牌
     */
    private String token;
    
    /**
     * 状态: detect/start/sentence_start/sentence_end/stop
     */
    private String state;
    
    /**
     * 文本内容
     */
    private String text;
    
    /**
     * 情绪标签
     */
    private String emotion;
    
    /**
     * 打断原因
     */
    private String reason;
    
    /**
     * 序号
     */
    private Integer sequence;
    
    /**
     * 是否最后一段
     */
    private Boolean isFinal;
}
