package com.bigbear.ai.platform.domain.vo;

import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型供应商视图对象
 */
@Data
@AutoMapper(target = ModelProviderEntity.class)
@Schema(description = "模型供应商视图对象")
public class ModelProviderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;

    /**
     * 模型类型（Memory/ASR/VAD/LLM/TTS）
     */
    @Schema(description = "模型类型（Memory/ASR/VAD/LLM/TTS）")
    private String modelType;

    /**
     * 供应器类型，如 openai、aliyun、doubao
     */
    @Schema(description = "供应器类型，如 openai、aliyun、doubao")
    private String providerCode;

    /**
     * 供应器名称
     */
    @Schema(description = "供应器名称")
    private String name;

    /**
     * 供应器字段列表（JSON格式）
     */
    @Schema(description = "供应器字段列表（JSON格式）")
    private String fields;

    /**
     * 排序
     */
    @Schema(description = "排序")
    private Integer sort;

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    private Long tenantId;

    /**
     * 删除标志（0正常 1已删除）
     */
    @Schema(description = "删除标志（0正常 1已删除）")
    private Integer delFlag;

    /**
     * 创建者
     */
    @Schema(description = "创建者")
    private Long creator;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createDate;

    /**
     * 更新者
     */
    @Schema(description = "更新者")
    private Long updater;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateDate;
}
