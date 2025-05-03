package com.xxs.order.api.common.enums;

import com.pig4cloud.plugin.excel.vo.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderType implements DictEnum {

    BUY("1", "购买订单"),
    GIFT("2", "赠送订单");
    // 必须有的字段
    private final String value;
    // 必须有的字段
    private final String label;
}
