package com.bigbear.aigc.biz.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MessageType {

    SYSTEM(0,""),
    USER(1,""),
    AI(2,""),
    TOOL_EXECUTION_RESULT(3,""),
    CUSTOM(4,"");

    private final Integer code;
    private final String name;
}
