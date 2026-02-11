package com.bigbear.mall.biz.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号启用DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class AccountEnableDTO {

    /**
     * 账号ID
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 模拟器ID
     */
    @NotNull(message = "模拟器ID不能为空")
    private Long emulatorId;
}
