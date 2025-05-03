package com.bigbear.order.infrastructure.entity.dto;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.converters.bigdecimal.BigDecimalStringConverter;
import cn.idev.excel.converters.integer.IntegerStringConverter;
import cn.idev.excel.converters.localdatetime.LocalDateTimeStringConverter;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.pig4cloud.plugin.excel.annotation.DictTypeProperty;
import com.pig4cloud.plugin.excel.annotation.ExcelLine;
import com.xxs.order.api.common.enums.GamesEnum;
import com.xxs.order.api.common.enums.OrderStatusEnum;
import com.xxs.order.api.common.enums.OrderType;
import com.xxs.order.api.common.enums.PayTypeEnum;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 订单表 excel 注意和全局一致
 *
 * @author pig code generator
 * @date 2023-05-09 13:57:32
 */
@Getter
@Setter
@ExcelIgnoreUnannotated
public class OrderExcelDTO extends Model<OrderExcelDTO> {

    @Serial
    private static final long serialVersionUID = -3419388197851305825L;

    /**
     * 导入时候回显行号
     */
    @ExcelLine
    @ExcelIgnore
    private Long lineNum;
    /**
     * 订单表主键
     */
    private Long id;

    /**
     * 三方订单流水号（为了更好地适配，使用String类型）
     * <p>
     * 给支付系统的也是这个，为了不暴露主键
     */
    @ExcelProperty("订单编号")
    private String orderNo;

    /**
     * 用户id
     */
    @ExcelProperty("用户账号")
    private String userId;

    @ExcelProperty("游戏类型")
    @DictTypeProperty(enums = GamesEnum.class)
    private String gameType;


    /**
     * 订单类型
     *
     * @see
     */
    @ExcelProperty(value = "订单类型")
    @DictTypeProperty(enums = OrderType.class)
    private String orderType;

    /**
     * 订单过期时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @cn.idev.excel.annotation.format.DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;


    /**
     * 订单状态
     */
    @ExcelProperty(value = "订单状态")
    @DictTypeProperty(enums = OrderStatusEnum.class)
    private String status;

    /**
     * 支付时间
     */
//    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    @ExcelProperty(value = "支付时间", converter = LocalDateTimeStringConverter.class)
//    @cn.idev.excel.annotation.format.DateTimeFormat("yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime payTime;
    @ExcelProperty(value = "支付时间" )
    private String payTime;

    /**
     * 支付渠道
     *
     * @see
     */
    @ExcelProperty(value = "支付渠道")
    @DictTypeProperty(enums = PayTypeEnum.class)
    private String payType;

    /**
     * 支付金额，单位 分 积分减免和优惠卷减免扣除后，最终支付价格
     */
//    @ExcelProperty(value = "支付金额", converter = BigDecimalStringConverter.class)
//    private BigDecimal payAmount;
    @ExcelProperty(value = "支付金额" )
    private String payAmount;

    /**
     * 订单备注
     */
    @ExcelProperty(value = "订单备注")
    private String remark;


    /**
     * 取消理由
     */
    @ExcelProperty("取消理由")
    private String cancelReason;

    /**
     * 取消时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "取消时间", converter = LocalDateTimeStringConverter.class)
    @cn.idev.excel.annotation.format.DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    /**
     * 退款时间
     */
    @ExcelProperty(value = "退款时间", converter = LocalDateTimeStringConverter.class)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @cn.idev.excel.annotation.format.DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundTime;

    /**
     * 退款渠道：系统退款，微信退款，支付宝退款
     */

    @ExcelProperty(value = "退款渠道")
    private String refundChannel;

    // 30,60 90
    @ExcelProperty(value = "套餐时长", converter = IntegerStringConverter.class)
    private Integer days;
}
