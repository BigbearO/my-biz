package com.bigbear.mall.biz.manager;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.mall.biz.domain.entity.AccountEntity;
import com.bigbear.mall.biz.domain.entity.OrderEntity;

import java.io.Serializable;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 订单基础服务接口
 * 继承 IService 提供基础 CRUD 能力
 */
public interface IOrderManager extends IService<OrderEntity> {

    /**
     * 根据订单号查询
     */
    default OrderEntity getByOrderNo(Serializable orderNo) {
        return lambdaQuery()
                .eq(OrderEntity::getOrderNo, orderNo)
                .last("limit 1")
                .one();
    }

    TableDataInfo<OrderEntity> selectPageList(OrderEntity config, PageQuery pageQuery);


    /**
     * 保存订单并发送MQ消息
     */
    void saveOrderInfo(OrderEntity orderEntity);
}
