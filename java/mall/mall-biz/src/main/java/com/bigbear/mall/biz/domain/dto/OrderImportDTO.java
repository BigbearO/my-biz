package com.bigbear.mall.biz.domain.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 订单导入DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class OrderImportDTO {

    /**
     * 账号
     */
    @ExcelProperty("订单编号")
    private String orderNo;


    @ExcelProperty("账号")
    private String account;

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
