package com.bigbear.order.infrastructure.entity.dto;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.converters.localdatetime.LocalDateTimeStringConverter;
import com.pig4cloud.plugin.excel.annotation.ExcelLine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderImportDTO {

    /**
     * 导入时候回显行号
     */
    @ExcelLine
    @ExcelIgnore
    private Long lineNum;
    /**
     * 三方订单流水号（为了更好地适配，使用String类型）
     * <p>
     * 给支付系统的也是这个，为了不暴露主键
     */
    @ExcelProperty("订单编号")
    private String orderNo;

    @ExcelProperty("买家昵称")
    private String userName;

    @ExcelProperty("游戏类型")
    private String gameType;

    @ExcelProperty("游戏账号")
    private String userAccount;


    @ExcelProperty("登录凭证")
    private String credential;

    /**
     * 创建时间
     */
    @Schema(description = "付款时间")
    @ExcelProperty(value = "付款时间",converter = LocalDateTimeStringConverter.class)
    private LocalDateTime payTime;
    @ExcelProperty(value = "下单时间",converter = LocalDateTimeStringConverter.class)
    private LocalDateTime orderTime;

    @ExcelProperty(value = "成交时间",converter = LocalDateTimeStringConverter.class)
    private LocalDateTime orderFinishTime;


    /**
     * 订单类型
     * @see
     */
    @ExcelProperty("订单游戏类型")
    private Integer orderType;


    @ExcelProperty("订单支付金额")
    private BigDecimal payAmount = BigDecimal.ZERO;

    /**
     * 订单备注
     */
    private String remark;

}
