package org.dromara.goodfish.meta.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号状态修改业务对象（启用 / 停用）.
 *
 * <p>★账号只停用、不删除：仅支持 normal / disabled 两个状态.</p>
 *
 * @author goodfish
 */
@Data
public class GoodfishAccountStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号状态：normal（启用）/ disabled（停用）
     */
    @NotBlank(message = "账号状态不能为空")
    private String status;

    /**
     * 备注（停用原因可选，暂不持久化到账号表，预留扩展）
     */
    private String remark;

    /**
     * 账号主键（路径参数回填，校验非空）
     */
    @NotNull(message = "主键不能为空")
    private Long id;

}
