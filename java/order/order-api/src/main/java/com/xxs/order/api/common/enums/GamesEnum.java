package com.xxs.order.api.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GamesEnum {

    YuanShen(0,"原神","原神")
    ;
    private final int code;
    private final String name;
    private final String desc;
}
