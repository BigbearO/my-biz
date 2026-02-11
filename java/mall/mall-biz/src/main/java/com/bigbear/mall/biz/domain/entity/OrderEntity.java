package com.bigbear.mall.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表
 *
 * @author mall code generator
 * @date 2025-02-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "trade_order", autoResultMap = true)
public class OrderEntity extends BaseEntity {

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
     * 订单类型
     */
    private Integer orderType;

    /**
     * 订单过期时间
     */
    private LocalDateTime expireTime;

    /** 取消理由 */
    private String cancelReason;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /**
     * 订单状态
     */
    private Integer status;

    /** 游戏类型 */
    private Integer gameType;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 支付渠道
     */
    private Integer payType;

    /**
     * 支付金额，单位 分
     * 积分减免和优惠卷减免扣除后，最终支付价格
     */
    private BigDecimal payAmount;

    /**
     * 订单备注
     */
    private String remark;

    /** 运费 */
    private BigDecimal freightFee;

    /** 退款状态 */
    private Integer refundStatus;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 退款渠道：系统退款，微信退款，支付宝退款 */
    private String refundChannel;

    private Integer days;
}
