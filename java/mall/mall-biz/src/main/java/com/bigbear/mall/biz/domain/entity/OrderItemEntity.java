package com.bigbear.mall.biz.domain.entity;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项表
 *
 * @author mall code generator
 * @date 2025-02-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "order_item", autoResultMap = true)
public class OrderItemEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 2149579684542820765L;

    /** 订单详情表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单表主键 */
    private Long orderId;

    /** 物流表主键，若无需发货，值为0 */
    private Long logisticsId;

    /** 收货人姓名 */
    private String consigneeName;

    /** 收货人电话 */
    private String consigneePhone;

    /** 收货地址 */
    private String consigneeAddress;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 商品ID */
    private Long commodityId;

    /** 商品名 */
    private String commodityName;

    /** 商品封面 */
    private String commodityCoverUrl;

    /** 购买单个商品的价格 */
    private BigDecimal commodityPrice = BigDecimal.ZERO;

    /** 购买商品花费的总积分 */
    private BigDecimal commodityPoints = BigDecimal.ZERO;

    /** 购买的商品数量 */
    private Integer commodityQuantity = 0;

    /** 折扣金额，如优惠券等 */
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** 商品小计金额，单价 * 数量 - 折扣 */
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** 额外信息 */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private JSONObject extraData;
}
