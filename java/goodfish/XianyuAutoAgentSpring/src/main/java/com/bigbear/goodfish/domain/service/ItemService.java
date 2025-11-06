package com.bigbear.goodfish.domain.service;

import com.bigbear.goodfish.domain.entity.Item;

/**
 * 商品服务接口
 *
 * @author autoagent
 */
public interface ItemService {

    /**
     * 保存或更新商品信息
     */
    Item saveOrUpdateItem(String itemId, String itemData, Double price, String description);

    /**
     * 获取商品信息
     */
    Item getItemById(String itemId);

    /**
     * 删除商品信息
     */
    void deleteItem(String itemId);
}
