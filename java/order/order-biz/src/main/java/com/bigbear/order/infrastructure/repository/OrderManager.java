package com.bigbear.order.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.order.infrastructure.entity.po.OrderPO;
import java.io.Serializable;

public interface OrderManager extends IService<OrderPO> {

    default OrderPO getByOrderNo(Serializable orderNo) {
        LambdaQueryWrapper<OrderPO> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(OrderPO::getOrderNo, orderNo)
                .last(" limit 1");
        return this.getOne(queryWrapper);

    }



    void saveOrderIno( OrderPO orderPO);

}
