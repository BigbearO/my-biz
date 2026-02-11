package com.bigbear.mall.biz.mapper;

import com.bigbear.mall.biz.domain.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 订单项Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapperPlus<OrderItemEntity, OrderItemEntity> {

}
