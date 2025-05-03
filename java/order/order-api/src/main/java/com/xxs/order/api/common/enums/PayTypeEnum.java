package com.xxs.order.api.common.enums;

import com.pig4cloud.plugin.excel.vo.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PayTypeEnum implements DictEnum {

    AliPay("1", "支付宝"),
    WX("2", "微信"),
    YinLian("3", "银联"),
    ;
    private final String value;

    private final String label;

}
