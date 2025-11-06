package com.bigbear.goodfish.infrastructure.service.impl;

import com.bigbear.goodfish.domain.entity.Item;
import com.bigbear.goodfish.domain.mapper.ItemMapper;
import com.bigbear.goodfish.domain.service.ItemService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现
 *
 * @author autoagent
 */
@Slf4j
@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    public ItemServiceImpl(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    public Item saveOrUpdateItem(String itemId, String itemData, Double price, String description) {
        Item item = itemMapper.selectByItemId(itemId);
        if (item == null) {
            item = new Item();
            item.setItemId(itemId);
            itemMapper.insert(item);
        }

        item.setData(itemData);
        item.setPrice(price);
        item.setDescription(description);

        itemMapper.updateById(item);
        return item;
    }

    @Override
    public Item getItemById(String itemId) {
        return itemMapper.selectByItemId(itemId);
    }

    @Override
    public void deleteItem(String itemId) {
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("item_id", itemId);
        itemMapper.delete(wrapper);
    }
}
