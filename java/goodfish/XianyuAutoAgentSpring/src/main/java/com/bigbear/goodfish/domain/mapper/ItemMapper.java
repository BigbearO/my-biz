package com.bigbear.goodfish.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.goodfish.domain.entity.Item;

/**
 * 商品Mapper
 *
 * @author autoagent
 */
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 根据商品ID查询商品信息
     */
    Item selectByItemId(String itemId);
}
