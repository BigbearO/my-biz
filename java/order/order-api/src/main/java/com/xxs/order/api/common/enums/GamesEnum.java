package com.xxs.order.api.common.enums;

import com.pig4cloud.plugin.excel.vo.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GamesEnum implements DictEnum {

    YuanShen("0", "原神"),
    ChenBaiJinQu("1", "尘白禁区"),
    ;
    private final String value;

    private final String label;

}
