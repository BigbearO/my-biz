package com.bigbear.order.service;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bigbear.order.infrastructure.AccountManager;
import com.bigbear.order.infrastructure.GameTaskSender;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountManager accountManager;

    private static volatile Map<Integer, GameTaskSender> gameTaskSenderServiceMap;


    public void task() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<AccountPO> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.ge(AccountPO::getStartTime, now);
        queryWrapper.le(AccountPO::getExpireTime, now);

        int pageNo = 1;
        int pageSize = 100;
        Page<AccountPO> res = null;
        do {
            res = accountManager.page(new Page<>(pageNo, pageSize), queryWrapper);
            if (CollectionUtils.isEmpty(res.getRecords())) {
                log.info("no data");
                break;
            }

            ++pageNo;

            doTask(res.getRecords());


        } while (res.hasNext());

    }

    void doTask(List<AccountPO> dataList) {
        // data 就是账号，根据这个来处理
//        根据不同type,发送到不同队列里面

        for (AccountPO accountPO : dataList) {

            try {
                GameTaskSender gameTaskSender = getGameTaskSender(accountPO.getType());
            } catch (Exception e) {

            }

        }

    }

    private GameTaskSender getGameTaskSender(Integer type) {

        if (gameTaskSenderServiceMap == null) {
            synchronized (GameTaskSender.class) {

                if (gameTaskSenderServiceMap == null) {
                    Collection<GameTaskSender> beans = SpringUtil.getBeansOfType(GameTaskSender.class).values();
                    gameTaskSenderServiceMap = beans.stream().collect(Collectors.toMap(GameTaskSender::type, gameTaskSender -> gameTaskSender, (v1, v2) -> v2));
                }
            }

        }


        return gameTaskSenderServiceMap.get(type);
    }
}
