package com.bigbear.mall.biz.domain.dto;


import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Excel导入账号DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class AccountImportDTO {

    /**
     * 账号
     */
    @ExcelProperty("账号")
    private String userAccount;

    /**
     * 游戏类型
     */
    @ExcelProperty("游戏类型")
    private Integer gameType;

    /**
     * 会员天数
     */
    @ExcelProperty("会员天数")
    private Integer days;
}
