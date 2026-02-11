package com.bigbear.mall.biz.manager;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.mall.biz.domain.entity.EmulatorEntity;
import com.bigbear.mall.biz.domain.vo.AvailableEmulatorVO;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * 模拟器基础服务接口
 *
 * @author bigbear
 * @date 2025-02-09
 */
public interface IEmulatorManager extends IService<EmulatorEntity> {

    /**
     * 分页查询模拟器列表
     *
     * @param entity    查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<EmulatorEntity> selectPageList(EmulatorEntity entity, PageQuery pageQuery);

    /**
     * 根据游戏类型查询可用模拟器列表
     *
     * @param gameType 游戏类型
     * @return 可用模拟器列表
     */
    List<AvailableEmulatorVO> selectAvailableEmulators(Integer gameType);

    /**
     * 查询模拟器当前承载的账号数量
     *
     * @param emulatorId 模拟器ID
     * @return 账号数量
     */
    Long selectEmulatorAccountCount(Long emulatorId);
}
