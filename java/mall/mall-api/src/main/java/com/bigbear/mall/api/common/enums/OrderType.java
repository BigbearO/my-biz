package com.bigbear.mall.api.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型枚举
 */
@Getter
@AllArgsConstructor
public enum OrderType {

    BUY("1", "购买订单"),
    GIFT("2", "赠送订单");

    private final String value;
    private final String label;
}
