package com.bigbear.order.infrastructure.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import com.bigbear.order.mapper.orderMapper;
import com.bigbear.order.infrastructure.OrderManager;
import org.springframework.stereotype.Service;

/**
 * 订单表
 *
 * @author xxs
 * @date 2025-04-21 22:32:38
 */
@Service
public class orderManagerImpl extends ServiceImpl<orderMapper, OrderPO> implements OrderManager {

}
