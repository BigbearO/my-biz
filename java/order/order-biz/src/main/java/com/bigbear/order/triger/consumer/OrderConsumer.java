package com.bigbear.order.triger.consumer;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bigbear.order.infrastructure.entity.po.AccountPO;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.bigbear.order.infrastructure.repository.AccountManager;
import com.bigbear.order.infrastructure.repository.OrderManager;
import com.xxs.order.api.common.constants.Games;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RocketMQMessageListener(topic = Games.ORDER, consumerGroup = "auto-games")
@RequiredArgsConstructor
public class OrderConsumer implements RocketMQListener<String> {
    private  final AccountManager accountManager;
    // todo 分布式事务 以及加锁
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String message) {
        log.info("开始计算账户会员时间 message: {}", JSONObject.toJSONString(message));
        OrderPO orderPO = JSONObject.parseObject(message, OrderPO.class);

//        获取账户信息
        AccountPO account = accountManager.getByAccount(orderPO.getUserId());

        // 计算账户会员时间
        LocalDateTime start = null;
        LocalDateTime expire = null;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(account.getExpireTime())) {
            expire = now.plusDays(orderPO.getDays()).with(LocalTime.MAX);
            start = now;
        } else {
            start = account.getStartTime();
            expire = account.getExpireTime().plusDays(orderPO.getDays()).with(LocalTime.MAX);
        }

        LambdaUpdateWrapper<AccountPO> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(AccountPO::getId, account.getId())
            .set(AccountPO::getStartTime, start)
            .set(AccountPO::getExpireTime, expire);
        boolean update = accountManager.update(updateWrapper);
    }
}


