package com.bigbear.order.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.order.entity.po.OrderItemPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper

public interface OrderItemMapper extends BaseMapper<OrderItemPO> {

}
