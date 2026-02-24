package com.bigbear.ai.platform.domain.vo;

import com.alibaba.fastjson2.JSONObject;
import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型配置视图对象
 */
@Data
@AutoMapper(target = ModelConfigEntity.class)
@Schema(description = "模型配置视图对象")
public class ModelConfigVo implements Serializable {

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
     * 模型编码（如AliLLM、DoubaoTTS）
     */
    @Schema(description = "模型编码（如AliLLM、DoubaoTTS）")
    private String modelCode;

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * 是否默认配置（0否 1是）
     */
    @Schema(description = "是否默认配置（0否 1是）")
    private Integer isDefault;

    /**
     * 是否启用（0禁用 1启用）
     */
    @Schema(description = "是否启用（0禁用 1启用）")
    private Integer isEnabled;

    /**
     * 模型配置（JSON格式）
     */
    @Schema(description = "模型配置（JSON格式）")
    private JSONObject configJson;

    /**
     * 官方文档链接
     */
    @Schema(description = "官方文档链接")
    private String docLink;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

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
