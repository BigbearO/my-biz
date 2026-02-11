package com.bigbear.mall.biz.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.manager.IOrderManager;
import com.bigbear.mall.biz.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单基础服务实现
 * 继承 ServiceImpl 获得基础 CRUD 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagerImpl extends ServiceImpl<OrderMapper, OrderEntity> implements IOrderManager {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public TableDataInfo<OrderEntity> selectPageList(OrderEntity config, PageQuery pageQuery) {
        LambdaQueryWrapper<OrderEntity> lqw = buildQueryWrapper(config);
        val result = this.baseMapper.selectPage(pageQuery.build(),lqw );

        return   TableDataInfo.build(result);
    }

    private LambdaQueryWrapper<OrderEntity> buildQueryWrapper(OrderEntity bo) {
        LambdaQueryWrapper<OrderEntity> lqw = Wrappers.lambdaQuery();
//        lqw.eq(StringUtils.isNotBlank(bo.getTenantId()), OrderEntity::getTenantId, bo.getTenantId());
//        lqw.like(StringUtils.isNotBlank(bo.getContactUserName()), OrderEntity::getContactUserName, bo.getContactUserName());

        lqw.eq(bo.getExpireTime() != null, OrderEntity::getExpireTime, bo.getExpireTime());

        lqw.eq(bo.getStatus() != null, OrderEntity::getStatus, bo.getStatus());
        lqw.eq(bo.getId() != null, OrderEntity::getId, bo.getId());
        lqw.orderByDesc(OrderEntity::getId);
        return lqw;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrderInfo(OrderEntity orderEntity) {
        this.save(orderEntity);
        // 发送MQ消息
        rocketMQTemplate.convertAndSend("order", JSONObject.toJSONString(orderEntity));
    }
}
