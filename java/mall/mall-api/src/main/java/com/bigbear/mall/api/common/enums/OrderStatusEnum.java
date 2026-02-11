package com.bigbear.mall.api.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PAYING("1", "待支付"),
    PAYED("2", "已支付");

    private final String value;
    private final String label;
}
