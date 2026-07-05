package com.bigbear.ai.biz.infra.manager;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.ai.biz.infra.mapper.ItemMapper;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现
 *
 * @author autoagent
 */
@Slf4j
@Service
public class ItemManager extends ServiceImpl<ItemMapper, ItemEntity> {

    public ItemEntity saveOrUpdateItem(String itemId, String itemData, Double price, String description) {
        ItemEntity item = this.selectByItemId(itemId);
        if (item == null) {
            item = new ItemEntity();
            item.setItemId(itemId);
           mapper.insert(item);
        }

        item.setData(itemData);
        item.setPrice(price);
        item.setDescription(description);
        mapper.update(item);
        return item;
    }


    public ItemEntity getItemById(String itemId) {
        return this.selectByItemId(itemId);
    }


    public void deleteItem(String itemId) {
        ItemEntity item = selectByItemId(itemId);
        if (item != null) {
            mapper.deleteById(item.getId());
        }
    }

    ItemEntity selectByItemId(String itemId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .from(ItemEntity.class)
            .where(ItemEntity::getItemId).eq(itemId)
            .limit(1);
        return mapper.selectOneByQuery(queryWrapper);
    }


}
