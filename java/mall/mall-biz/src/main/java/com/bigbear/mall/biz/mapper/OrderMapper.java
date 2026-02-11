package com.bigbear.mall.biz.mapper;

import com.bigbear.mall.biz.domain.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapperPlus<OrderEntity, OrderEntity> {

}
