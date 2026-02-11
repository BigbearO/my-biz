package com.bigbear.mall.biz.trigger.consumer;

import com.alibaba.fastjson.JSONObject;
import com.bigbear.mall.api.common.constants.Games;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.manager.IAccountManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 订单消息消费者
 * 处理订单完成后的账号会员时间计算
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = Games.ORDER, consumerGroup = "mall-auto-games")
@RequiredArgsConstructor
public class OrderConsumer implements RocketMQListener<String> {

    private final IAccountManager accountManager;

    // TODO: 分布式事务以及加锁
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String message) {
        log.info("开始计算账户会员时间 message: {}", JSONObject.toJSONString(message));
        OrderEntity orderEntity = JSONObject.parseObject(message, OrderEntity.class);

        // 获取账户信息
        AccountEntity account = accountManager.getByAccount(orderEntity.getUserId());

        // 计算账户会员时间
        LocalDateTime start;
        LocalDateTime expire;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(account.getExpireTime())) {
            expire = now.plusDays(orderEntity.getDays()).with(LocalTime.MAX);
            start = now;
        } else {
            start = account.getStartTime();
            expire = account.getExpireTime().plusDays(orderEntity.getDays()).with(LocalTime.MAX);
        }

        accountManager.lambdaUpdate()
                .eq(AccountEntity::getId, account.getId())
                .set(AccountEntity::getStartTime, start)
                .set(AccountEntity::getExpireTime, expire)
                .update();

        log.info("账户会员时间计算完成，account: {}, start: {}, expire: {}",
                account.getUserAccount(), start, expire);
    }
}
