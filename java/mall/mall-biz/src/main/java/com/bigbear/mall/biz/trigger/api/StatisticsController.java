package com.bigbear.mall.biz.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.mall.biz.application.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计控制器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/statistics")
@Tag(name = "统计管理", description = "统计管理")
public class StatisticsController extends BaseController {

    private final StatisticsService statisticsService;

    /**
     * 月度统计
     */
    @Operation(summary = "月度统计", description = "按月统计账号有效执行次数")
    @SaCheckPermission("mall:statistics:monthly")
    @GetMapping("/monthly")
    public R<StatisticsService.MonthlyStatisticsResult> monthly(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Integer gameType) {
        return R.ok(statisticsService.getMonthlyStatistics(yearMonth, accountId, gameType));
    }
}
