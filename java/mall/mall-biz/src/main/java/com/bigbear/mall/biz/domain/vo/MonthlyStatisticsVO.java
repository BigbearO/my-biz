package com.bigbear.mall.biz.domain.vo;

import lombok.Data;

/**
 * 月度统计VO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class MonthlyStatisticsVO {

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 游戏类型
     */
    private Integer gameType;

    /**
     * 游戏名称
     */
    private String gameName;

    /**
     * 有效天数（该月内每天只算1次）
     */
    private Long validDays;
}
