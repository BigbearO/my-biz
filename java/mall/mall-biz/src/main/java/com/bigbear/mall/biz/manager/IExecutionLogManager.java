package com.bigbear.mall.biz.manager;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.mall.biz.domain.entity.ExecutionLogEntity;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 执行日志基础服务接口
 *
 * @author bigbear
 * @date 2025-02-09
 */
public interface IExecutionLogManager extends IService<ExecutionLogEntity> {

    /**
     * 分页查询执行日志列表
     *
     * @param entity    查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<ExecutionLogEntity> selectPageList(ExecutionLogEntity entity, PageQuery pageQuery);
}
