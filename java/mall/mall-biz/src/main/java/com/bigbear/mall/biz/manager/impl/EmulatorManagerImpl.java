package com.bigbear.mall.biz.manager.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.EmulatorEntity;
import com.bigbear.mall.biz.domain.vo.AvailableEmulatorVO;
import com.bigbear.mall.biz.manager.IAccountManager;
import com.bigbear.mall.biz.manager.IEmulatorManager;
import com.bigbear.mall.biz.mapper.EmulatorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模拟器基础服务实现
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmulatorManagerImpl extends ServiceImpl<EmulatorMapper, EmulatorEntity> implements IEmulatorManager {

    private final IAccountManager accountManager;

    @Override
    public TableDataInfo<EmulatorEntity> selectPageList(EmulatorEntity entity, PageQuery pageQuery) {
        return TableDataInfo.build(
                lambdaQuery()
                        .like(entity.getName() != null, EmulatorEntity::getName, entity.getName())
                        .eq(entity.getGameType() != null, EmulatorEntity::getGameType, entity.getGameType())
                        .eq(entity.getEnabled() != null, EmulatorEntity::getEnabled, entity.getEnabled())
                        .orderByDesc(EmulatorEntity::getCreateTime)
                        .page(pageQuery.build())
        );
    }

    @Override
    public List<AvailableEmulatorVO> selectAvailableEmulators(Integer gameType) {
        // 查询指定游戏类型的启用中的模拟器
        List<EmulatorEntity> emulators = lambdaQuery()
                .eq(EmulatorEntity::getGameType, gameType)
                .eq(EmulatorEntity::getEnabled, true)
                .list();

        return emulators.stream().map(emulator -> {
            AvailableEmulatorVO vo = new AvailableEmulatorVO();
            vo.setId(emulator.getId());
            vo.setName(emulator.getName());
            vo.setIp(emulator.getIp());
            vo.setPort(emulator.getPort());
            vo.setGameType(emulator.getGameType());
            vo.setMaxCount(emulator.getMaxCount());

            // 查询当前已分配账号数
            Long currentCount = selectEmulatorAccountCount(emulator.getId());
            vo.setCurrentCount(currentCount);
            vo.setAvailableCount(emulator.getMaxCount() - currentCount);

            return vo;
        }).filter(vo -> vo.getAvailableCount() > 0).collect(Collectors.toList());
    }

    @Override
    public Long selectEmulatorAccountCount(Long emulatorId) {
        return accountManager.lambdaQuery()
                .eq(AccountEntity::getEmulatorId, emulatorId)
                .eq(AccountEntity::getEnabled, true)
                .count();
    }
}
