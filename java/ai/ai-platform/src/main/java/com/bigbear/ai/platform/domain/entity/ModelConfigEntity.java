package com.bigbear.ai.platform.domain.entity;

import com.alibaba.fastjson2.JSONObject;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置实体类
 */
@Data
@Table("ai_model_config")
public class ModelConfigEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private String id;

    /**
     * 模型类型（Memory/ASR/VAD/LLM/TTS）
     */
    private String modelType;

    /**
     * 模型编码（如AliLLM、DoubaoTTS）
     */
    private String modelCode;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 是否默认配置（0否 1是）
     */
    private Integer isDefault;

    /**
     * 是否启用（0禁用 1启用）
     */
    private Integer isEnabled;

    /**
     * 模型配置（JSON格式）
     */
    private JSONObject configJson;

    /**
     * 官方文档链接
     */
    private String docLink;

    /**
     * 备注
     */
    private String remark;

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
    private LocalDateTime createDate;

    /**
     * 更新者
     */
    private Long updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateDate;
}
