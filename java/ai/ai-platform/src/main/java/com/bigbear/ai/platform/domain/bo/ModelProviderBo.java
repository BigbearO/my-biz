package com.bigbear.ai.platform.domain.bo;

import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型供应商业务对象
 */
@Data
@AutoMapper(target = ModelProviderEntity.class, reverseConvertGenerate = false)
public class ModelProviderBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @NotBlank(message = "供应商ID不能为空", groups = { EditGroup.class })
    private String id;

    /**
     * 模型类型（Memory/ASR/VAD/LLM/TTS）
     */
    @NotBlank(message = "模型类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String modelType;

    /**
     * 供应器类型，如 openai、aliyun、doubao
     */
    @NotBlank(message = "供应器类型不能为空", groups = { AddGroup.class, EditGroup.class })
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
