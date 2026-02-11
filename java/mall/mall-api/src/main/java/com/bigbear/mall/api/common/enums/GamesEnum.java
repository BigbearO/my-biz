package com.bigbear.mall.api.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游戏类型枚举
 */
@Getter
@AllArgsConstructor
public enum GamesEnum {

    YuanShen("0", "原神"),
    ChenBaiJinQu("1", "尘白禁区"),
    ;

    private final String value;
    private final String label;
}
