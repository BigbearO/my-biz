package com.bigbear.mall.biz.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游戏类型枚举
 */
@Getter
@AllArgsConstructor
public enum GameTypeEnums {

    YUAN_SHEN(0, ""),
    HONKAI(1, "崩铁"),
    ;

    final Integer code;
    final String name;
}
