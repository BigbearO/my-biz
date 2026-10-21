package org.dromara.goodfish.admin.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账号停用/启用业务对象.
 *
 * @author goodfish
 */
@Data
public class AdminAccountStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 账号状态：normal（启用）/ disabled（停用）
     */
    @NotBlank(message = "账号状态不能为空")
    private String status;

}
