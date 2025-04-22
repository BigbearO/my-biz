package com.bigbear.order.manager.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.order.entity.po.OrderPO;
import com.bigbear.order.mapper.orderMapper;
import com.bigbear.order.manager.OrderManager;
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
