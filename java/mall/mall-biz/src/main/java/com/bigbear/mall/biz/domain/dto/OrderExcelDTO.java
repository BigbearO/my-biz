package com.bigbear.mall.biz.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单Excel导入导出DTO
 */
@Data
public class OrderExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 三方订单流水号
     */
    private String orderNo;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 订单类型
     */
    private Integer orderType;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 游戏类型
     */
    private Integer gameType;

    /**
     * 支付渠道
     */
    private Integer payType;

    /**
     * 支付金额
     */
    private BigDecimal payAmount;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 运费
     */
    private BigDecimal freightFee;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 行号（Excel导入用）
     */
    private Long lineNum;
}
