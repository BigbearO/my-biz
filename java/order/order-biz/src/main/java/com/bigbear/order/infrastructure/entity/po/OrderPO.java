package com.bigbear.order.infrastructure.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表
 *
 * @author pig code generator
 * @date 2023-05-09 13:57:32
 */
@Getter
@Setter
@TableName(value = "`trade_order`",autoResultMap = true)
public class OrderPO extends Model<OrderPO>  {
    @Serial
    private static final long serialVersionUID = -4519388197851305825L;

    /** 订单表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 三方订单流水号（为了更好地适配，使用String类型）
     * <p>
     * 给支付系统的也是这个，为了不暴露主键
     */
    private String orderNo;

    /** 用户id */
    private String userId;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    /**
     * 订单类型
     *
     * @see
     */
    private Integer orderType;

    /**
     * 订单过期时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 取消理由 */
    private String cancelReason;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /**
     * 订单状态
     *
     */
    private Integer status;

    /**
     * 支付时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /**
     * 支付渠道
     *
     * @see
     */
    private Integer payType;

    /**
     * 支付金额，单位 分
     * 积分减免和优惠卷减免扣除后，最终支付价格
     */
    private BigDecimal payAmount = BigDecimal.ZERO;

    /**
     * 订单备注
     */
    private String remark;

    /** 运费 */
    private BigDecimal freightFee = BigDecimal.ZERO;

    /** 退款状态 */
    private Integer refundStatus;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 退款渠道：系统退款，微信退款，支付宝退款 */
    private String refundChannel;
}
