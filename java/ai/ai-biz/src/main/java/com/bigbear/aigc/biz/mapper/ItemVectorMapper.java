package com.bigbear.aigc.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.aigc.biz.domain.entity.ItemVectorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface   ItemVectorMapper extends BaseMapper<ItemVectorEntity> {
    ItemVectorEntity selectByItemIdAndTenantId(@Param("itemId") String itemId, @Param("tenantId") String tenantId);
}
