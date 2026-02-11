package com.bigbear.mall.biz.application.service;

import com.bigbear.mall.api.common.enums.GamesEnum;
import com.bigbear.mall.biz.domain.vo.GameTypeStatisticsVO;
import com.bigbear.mall.biz.domain.vo.MonthlyStatisticsVO;
import com.bigbear.mall.biz.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ExecutionLogMapper executionLogMapper;

    /**
     * 按月统计执行次数
     *
     * @param yearMonth 年月（格式：2025-02）
     * @param accountId 账号ID（可选）
     * @param gameType  游戏类型（可选）
     * @return 统计结果
     */
    public MonthlyStatisticsResult getMonthlyStatistics(String yearMonth, Long accountId, Integer gameType) {
        MonthlyStatisticsResult result = new MonthlyStatisticsResult();
        result.setYearMonth(yearMonth);

        // 1. 查询总有效次数
        Long totalCount = executionLogMapper.selectTotalValidCount(yearMonth, gameType);
        result.setTotalCount(totalCount != null ? totalCount : 0L);

        // 2. 按游戏类型统计
        List<GameTypeStatisticsVO> gameTypeStats = executionLogMapper.selectStatisticsByGameType(yearMonth);
        // 设置游戏名称
        gameTypeStats.forEach(stat -> {
            GamesEnum gameEnum = getGameEnumByValue(stat.getGameType());
            if (gameEnum != null) {
                stat.setGameName(gameEnum.getLabel());
            }
        });
        result.setGameTypeStats(gameTypeStats);

        // 3. 查询账号统计明细
        List<MonthlyStatisticsVO> accountStats = executionLogMapper.selectMonthlyStatistics(yearMonth, accountId, gameType);
        // 设置游戏名称
        accountStats.forEach(stat -> {
            GamesEnum gameEnum = getGameEnumByValue(stat.getGameType());
            if (gameEnum != null) {
                stat.setGameName(gameEnum.getLabel());
            }
        });
        result.setAccountStats(accountStats);

        return result;
    }

    /**
     * 根据游戏类型值获取枚举
     */
    private GamesEnum getGameEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (GamesEnum e : GamesEnum.values()) {
            if (Integer.valueOf(e.getValue()).equals(value)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 月度统计结果
     */
    @lombok.Data
    public static class MonthlyStatisticsResult {
        private String yearMonth;
        private Long totalCount;
        private List<GameTypeStatisticsVO> gameTypeStats;
        private List<MonthlyStatisticsVO> accountStats;
    }
}
