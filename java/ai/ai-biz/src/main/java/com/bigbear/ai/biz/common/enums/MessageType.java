package com.bigbear.ai.biz.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MessageType {

    SYSTEM(0,"SYSTEM"),
    USER(1,"USER"),
    AI(2,""),
    TOOL_EXECUTION_RESULT(3,""),
    CUSTOM(4,"");

    private final Integer code;
    private final String name;
}
