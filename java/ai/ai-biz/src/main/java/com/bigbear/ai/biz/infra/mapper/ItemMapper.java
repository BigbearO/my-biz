package com.bigbear.ai.biz.infra.mapper;

import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface ItemMapper extends BaseMapper<ItemEntity> {

    /**
     * 根据商品ID查询商品信息
     */
    ItemEntity selectByItemId(String itemId);
}
