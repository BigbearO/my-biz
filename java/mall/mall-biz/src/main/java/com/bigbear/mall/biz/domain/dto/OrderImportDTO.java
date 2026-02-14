package com.bigbear.mall.biz.domain.dto;

import cn.idev.excel.annotation.ExcelProperty;
import com.bigbear.mall.biz.converter.GameTypeEnumConverter;
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
     * 订单编号
     */
    @ExcelProperty("订单编号")
    private String orderNo;

    /**
     * 账号
     */
    @ExcelProperty("账号")
    private String account;

    /**
     * 游戏类型
     * Excel 显示：原神/尘白禁区
     * Java 存储：0/1 (int)
     */
    @ExcelProperty(value = "游戏类型", converter = GameTypeEnumConverter.class)
    private Integer gameType;

    /**
     * 会员天数
     */
    @ExcelProperty("会员天数")
    private Integer days;
}
