package com.bigbear.ai.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体配置实体类
 */
@Data
@TableName("ai_agent")
public class AgentEntity {

    @TableId
    private String id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 智能体编码
     */
    private String agentCode;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 语音识别模型标识
     */
    private String asrModelId;

    /**
     * 语音活动检测标识
     */
    private String vadModelId;

    /**
     * 大语言模型标识
     */
    private String llmModelId;

    /**
     * VLLM模型标识
     */
    private String vllmModelId;

    /**
     * 语音合成模型标识
     */
    private String ttsModelId;

    /**
     * 音色标识
     */
    private String ttsVoiceId;

    /**
     * 记忆模型标识
     */
    private String memModelId;

    /**
     * 意图模型标识
     */
    private String intentModelId;

    /**
     * 聊天记录配置（0不记录 1仅记录文本 2记录文本和语音）
     */
    private Integer chatHistoryConf;

    /**
     * 角色设定参数
     */
    private String systemPrompt;

    /**
     * 总结记忆
     */
    private String summaryMemory;

    /**
     * 语言编码
     */
    private String langCode;

    /**
     * 交互语种
     */
    private String language;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 删除标志（0正常 1已删除）
     */
    private Integer delFlag;

    /**
     * 创建者
     */
    private Long creator;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新者
     */
    private Long updater;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
