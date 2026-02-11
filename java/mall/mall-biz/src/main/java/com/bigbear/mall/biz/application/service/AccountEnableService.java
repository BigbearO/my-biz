package com.bigbear.mall.biz.application.service;

import com.bigbear.mall.biz.domain.dto.AccountEnableDTO;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.EmulatorEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import com.bigbear.mall.biz.manager.IEmulatorManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 账号启用服务
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountEnableService {

    private final IAccountManager accountManager;
    private final IEmulatorManager emulatorManager;

    /**
     * 启用账号并分配模拟器
     *
     * @param dto 启用参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableAccount(AccountEnableDTO dto) {
        Long accountId = dto.getAccountId();
        Long emulatorId = dto.getEmulatorId();

        // 1. 查询账号
        AccountEntity account = accountManager.getById(accountId);
        if (account == null) {
            throw new ServiceException("账号不存在");
        }

        // 2. 查询模拟器
        EmulatorEntity emulator = emulatorManager.getById(emulatorId);
        if (emulator == null) {
            throw new ServiceException("模拟器不存在");
        }
        if (!Boolean.TRUE.equals(emulator.getEnabled())) {
            throw new ServiceException("模拟器未启用");
        }

        // 3. 校验游戏类型匹配
        if (!Objects.equals(account.getType(), emulator.getGameType())) {
            throw new ServiceException("模拟器游戏类型与账号不匹配");
        }

        // 4. 校验模拟器是否已达上限
        Long currentCount = emulatorManager.selectEmulatorAccountCount(emulatorId);
        if (currentCount >= emulator.getMaxCount()) {
            throw new ServiceException("该模拟器已达承载上限");
        }

        // 5. 更新账号状态
        accountManager.lambdaUpdate()
                .set(AccountEntity::getEnabled, true)
                .set(AccountEntity::getEmulatorId, emulatorId)
                .eq(AccountEntity::getId, accountId)
                .update();

        log.info("账号启用成功，accountId={}, emulatorId={}", accountId, emulatorId);
    }
}
