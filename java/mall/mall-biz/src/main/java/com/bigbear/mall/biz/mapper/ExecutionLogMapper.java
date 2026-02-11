package com.bigbear.mall.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.bigbear.mall.biz.domain.entity.ExecutionLogEntity;
import com.bigbear.mall.biz.domain.vo.GameTypeStatisticsVO;
import com.bigbear.mall.biz.domain.vo.MonthlyStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 执行日志Mapper
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Mapper
public interface ExecutionLogMapper extends BaseMapperPlus<ExecutionLogEntity, ExecutionLogEntity> {

    /**
     * 按月统计账号有效执行次数
     * 每个账号每天只算1次有效计数
     *
     * @param yearMonth 年月（格式：2025-02）
     * @param accountId 账号ID（可选）
     * @param gameType  游戏类型（可选）
     * @return 统计结果列表
     */
    @InterceptorIgnore
    List<MonthlyStatisticsVO> selectMonthlyStatistics(
            @Param("yearMonth") String yearMonth,
            @Param("accountId") Long accountId,
            @Param("gameType") Integer gameType
    );

    /**
     * 统计总有效次数
     *
     * @param yearMonth 年月（格式：2025-02）
     * @param gameType  游戏类型（可选）
     * @return 总有效次数
     */
    Long selectTotalValidCount(
            @Param("yearMonth") String yearMonth,
            @Param("gameType") Integer gameType
    );

    /**
     * 按游戏类型统计有效次数
     *
     * @param yearMonth 年月（格式：2025-02）
     * @return 各游戏类型统计结果
     */
    List<GameTypeStatisticsVO> selectStatisticsByGameType(
            @Param("yearMonth") String yearMonth
    );
}
