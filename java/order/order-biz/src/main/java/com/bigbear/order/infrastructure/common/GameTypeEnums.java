package com.bigbear.order.infrastructure.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GameTypeEnums {

    YUAN_SHEN(0,"")
    ;

    final Integer code;
    final String name;
}
