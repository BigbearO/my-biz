package com.xxs.order.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.pig4cloud.pig.common.mybatis.base.BaseEntity;
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
public class OrderPO extends BaseEntity {
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

    /** 操作用户，应该使用update-by字段，但是框架当前对于app用户自动填充是anonymous */
    private String cancelUser;

    /**
     * 订单状态
     *
     * @see
     */
    private Integer orderStatus;

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

    /** 物流表主键，若无需发货，值为0 */
    private Long logisticsId;

    /** 收货人姓名 */
    private String consigneeName;

    /** 收货人电话 */
    private String consigneePhone;

    /** 收货地址 */
    private String consigneeAddress;

    /** 发货时间：new */
    private LocalDateTime deliveryTime;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 支付总积分
     */
    private BigDecimal payTotalPoints;

    /** 运费 */
    private BigDecimal freightFee = BigDecimal.ZERO;

    /** 退款状态 */
    private Integer refundStatus;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 退款渠道：系统退款，微信退款，支付宝退款 */
    private String refundChannel;
}
