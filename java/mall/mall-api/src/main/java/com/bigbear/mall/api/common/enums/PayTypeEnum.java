package com.bigbear.mall.api.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付方式枚举
 */
@Getter
@AllArgsConstructor
public enum PayTypeEnum {

    AliPay("1", "支付宝"),
    WX("2", "微信"),
    YinLian("3", "银联"),
    ;

    private final String value;
    private final String label;
}
