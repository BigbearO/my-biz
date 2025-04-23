package com.bigbear.order.infrastructure.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.order.infrastructure.entity.po.OrderItemPO;
import com.bigbear.order.infrastructure.OrderItemManager;
import com.bigbear.order.mapper.OrderItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 订单详情表
 *
 * @author bigbear
 * @date 2025-04-22 09:35:17
 */
@Slf4j
@Service
public class OrderItemManagerImpl extends ServiceImpl<OrderItemMapper, OrderItemPO> implements OrderItemManager {

}
