package com.bigbear.mall.biz.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.mall.biz.domain.entity.ExecutionLogEntity;
import com.bigbear.mall.biz.manager.IExecutionLogManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 执行日志控制器
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/executionLog")
@Tag(name = "执行日志管理", description = "执行日志管理")
public class ExecutionLogController extends BaseController {

    private final IExecutionLogManager executionLogManager;

    /**
     * 分页查询执行日志列表
     */
    @Operation(summary = "分页查询", description = "分页查询执行日志列表")
    @SaCheckPermission("mall:executionLog:list")
    @GetMapping("/list")
    public TableDataInfo<ExecutionLogEntity> page(ExecutionLogEntity log, PageQuery pageQuery) {
        return executionLogManager.selectPageList(log, pageQuery);
    }

    /**
     * 获取执行日志详情
     */
    @Operation(summary = "获取详情", description = "获取执行日志详情")
    @SaCheckPermission("mall:executionLog:query")
    @GetMapping("/{id}")
    public R<ExecutionLogEntity> getInfo(@PathVariable Long id) {
        return R.ok(executionLogManager.getById(id));
    }
}
