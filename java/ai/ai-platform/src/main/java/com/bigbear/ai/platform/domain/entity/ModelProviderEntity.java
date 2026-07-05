package com.bigbear.ai.platform.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商实体类
 */
@Data
@Table("ai_model_provider")
public class ModelProviderEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private String id;

    /**
     * 模型类型（Memory/ASR/VAD/LLM/TTS）
     */
    private String modelType;

    /**
     * 供应器类型，如 openai、aliyun、doubao
     */
    private String providerCode;

    /**
     * 供应器名称
     */
    private String name;

    /**
     * 供应器字段列表（JSON格式）
     */
    private String fields;

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
