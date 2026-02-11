package com.bigbear.mall.biz.manager.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.mall.biz.domain.entity.ExecutionLogEntity;
import com.bigbear.mall.biz.manager.IExecutionLogManager;
import com.bigbear.mall.biz.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

/**
 * 执行日志基础服务实现
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionLogManagerImpl extends ServiceImpl<ExecutionLogMapper, ExecutionLogEntity> implements IExecutionLogManager {

    @Override
    public TableDataInfo<ExecutionLogEntity> selectPageList(ExecutionLogEntity entity, PageQuery pageQuery) {
        return TableDataInfo.build(
                lambdaQuery()
                        .eq(entity.getAccountId() != null, ExecutionLogEntity::getAccountId, entity.getAccountId())
                        .eq(entity.getEmulatorId() != null, ExecutionLogEntity::getEmulatorId, entity.getEmulatorId())
                        .eq(entity.getGameType() != null, ExecutionLogEntity::getGameType, entity.getGameType())
                        .eq(entity.getStatus() != null, ExecutionLogEntity::getStatus, entity.getStatus())
                        .orderByDesc(ExecutionLogEntity::getCreateTime)
                        .page(pageQuery.build())
        );
    }
}
