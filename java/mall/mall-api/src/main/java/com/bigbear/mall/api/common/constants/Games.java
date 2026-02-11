package com.bigbear.mall.api.common.constants;

import java.time.LocalDateTime;

/**
 * 游戏相关常量
 */
public interface Games {

    String YUAN_SHEN = "0";
    String CHEN_BAI_JIN_QU = "1";

    String ORDER = "order";

    LocalDateTime DEFAULT_ACCOUNT_TIME = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
}
