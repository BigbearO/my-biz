package com.xxs.order.entity.po;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import com.pig4cloud.pig.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 订单项表
 *
 * @author pig code generator
 * @date 2023-05-09 13:57:32
 */
@Getter
@Setter
@TableName(value = "order_item", autoResultMap = true)
@Schema(description = "订单项表")
public class OrderItemPO extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 2149579684542820765L;

    /** 订单详情表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单表主键 */
    private Long orderId;

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

    /** 额外信息   */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private JSONObject extraData;
}
