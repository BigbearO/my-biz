package com.bigbear.mall.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行级错误DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RowErrorDTO {

    /**
     * Excel行号
     */
    private Integer rowNum;

    /**
     * 账号
     */
    private String account;

    /**
     * 错误信息
     */
    private String errorMsg;
}
