package com.bigbear.ai.biz.infra.mapper;

import com.mybatisflex.core.BaseMapper;
import com.bigbear.ai.biz.domain.entity.ItemVectorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface   ItemVectorMapper extends BaseMapper<ItemVectorEntity> {
    ItemVectorEntity selectByItemIdAndTenantId(@Param("itemId") String itemId, @Param("tenantId") String tenantId);
}
