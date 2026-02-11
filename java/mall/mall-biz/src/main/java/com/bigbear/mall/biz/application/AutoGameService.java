package com.bigbear.mall.biz.application;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.mall.biz.common.GameTaskSender;
import com.bigbear.mall.biz.domain.dto.MQMessageDTO;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.EmulatorEntity;
import com.bigbear.mall.biz.domain.entity.ExecutionLogEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import com.bigbear.mall.biz.manager.IEmulatorManager;
import com.bigbear.mall.biz.manager.IExecutionLogManager;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动游戏任务服务
 * 扫描有效期的已启用账号，发送到对应游戏队列进行处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoGameService {

    private final IAccountManager accountManager;
    private final IEmulatorManager emulatorManager;
    private final IExecutionLogManager executionLogManager;

    private static volatile Map<Integer, GameTaskSender> gameTaskSenderServiceMap;

    /**
     * 定时任务入口
     * 扫描当前所有在期限内且已启用的账号，根据不同的游戏类型发送到对应MQ
     */
    public void task() {
        LocalDateTime now = LocalDateTime.now();

        int pageNo = 1;
        int pageSize = 100;
        Page<AccountEntity> res;

        do {
            res = accountManager.lambdaQuery()
                    .le(AccountEntity::getStartTime, now)
                    .ge(AccountEntity::getExpireTime, now)
                    .eq(AccountEntity::getEnabled, true)
                    .isNotNull(AccountEntity::getEmulatorId)
                    .page(new Page<>(pageNo, pageSize));

            if (res.getRecords().isEmpty()) {
                log.info("没有需要处理的游戏账号数据");
                break;
            }

            ++pageNo;
            doTask(res.getRecords());
        } while (res.hasNext());
    }

    /**
     * 处理账号列表
     */
    void doTask(List<AccountEntity> dataList) {
        for (AccountEntity account : dataList) {
            try {
                processAccount(account);
            } catch (Exception e) {
                log.warn("自动任务处理失败, 账号: {}, 游戏类型: {}",
                        account.getUserAccount(), account.getType(), e);
            }
        }
    }

    /**
     * 处理单个账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void processAccount(AccountEntity account) {
        // 1. 查询模拟器信息
        EmulatorEntity emulator = emulatorManager.getById(account.getEmulatorId());
        if (emulator == null || !Boolean.TRUE.equals(emulator.getEnabled())) {
            log.warn("账号{}的模拟器不可用，跳过", account.getId());
            return;
        }

        // 2. 构建MQ消息
        MQMessageDTO message = MQMessageDTO.builder()
                .configName("Alas")
                .command("daily_quest")
                .emulatorIp(emulator.getIp())
                .emulatorPort(String.valueOf(emulator.getPort()))
                .priority(1)
                .accountId(account.getId())
                .build();

        var gameTaskSender = getGameTaskSender(account.getType());

        // 3. 发送MQ
        gameTaskSender.send2Auto(message, account.getType());

        // 4. 记录执行日志
        ExecutionLogEntity logEntity = new ExecutionLogEntity();
        logEntity.setAccountId(account.getId());
        logEntity.setEmulatorId(emulator.getId());
        logEntity.setGameType(account.getType());
        logEntity.setCommand("daily_quest");
        logEntity.setStatus(0); // 待执行
        logEntity.setMqMessage(JSON.toJSONString(message));
        logEntity.setExecuteTime(LocalDateTime.now());
        executionLogManager.save(logEntity);

        log.debug("账号任务发送成功，accountId={}, emulatorId={}", account.getId(), emulator.getId());
    }


    private GameTaskSender getGameTaskSender(Integer type) {

        if (gameTaskSenderServiceMap == null) {
            synchronized (GameTaskSender.class) {
                if (gameTaskSenderServiceMap == null) {
                    Collection<GameTaskSender> beans = SpringUtil.getBeansOfType(
                        GameTaskSender.class).values();
                    gameTaskSenderServiceMap = beans.stream().collect(
                        Collectors.toMap(GameTaskSender::type, gameTaskSender -> gameTaskSender,
                            (v1, v2) -> v2));
                }
            }
        }

        return gameTaskSenderServiceMap.get(type);
    }
}
