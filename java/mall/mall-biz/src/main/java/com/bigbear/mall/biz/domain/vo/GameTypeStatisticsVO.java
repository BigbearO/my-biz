package com.bigbear.mall.biz.domain.vo;

import lombok.Data;

/**
 * 游戏类型统计VO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class GameTypeStatisticsVO {

    /**
     * 游戏类型
     */
    private Integer gameType;

    /**
     * 游戏名称
     */
    private String gameName;

    /**
     * 有效次数
     */
    private Long count;
}
