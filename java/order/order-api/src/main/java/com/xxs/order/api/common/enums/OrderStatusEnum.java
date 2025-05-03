package com.xxs.order.api.common.enums;

import com.pig4cloud.plugin.excel.vo.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatusEnum implements DictEnum {

    PAYING("1", "待支付"),
    PAYED("2", "已支付");
    // 必须有的字段
    private final String value;
    // 必须有的字段
    private final String label;
}
