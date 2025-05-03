package com.bigbear.order.infrastructure.repository.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.bigbear.order.mapper.orderMapper;
import com.bigbear.order.infrastructure.repository.OrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单表
 *
 * @author xxs
 * @date 2025-04-21 22:32:38
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class orderManagerImpl extends ServiceImpl<orderMapper, OrderPO> implements OrderManager {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    @Transactional
    public  void saveOrderIno(OrderPO orderPO){

        this.save(orderPO);
        //todo dto化
        rocketMQTemplate.convertAndSend("order", JSONObject.toJSONString(orderPO));

    }
}
