package com.bigbear.ai.biz.infra.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
            baseMapper.insert(item);
        }

        item.setData(itemData);
        item.setPrice(price);
        item.setDescription(description);

        baseMapper.updateById(item);
        return item;
    }


    public ItemEntity getItemById(String itemId) {
        return this.selectByItemId(itemId);
    }


    public void deleteItem(String itemId) {
        this.remove(baseItemIdWrapper(itemId));
    }

    ItemEntity selectByItemId(String itemId) {
        LambdaQueryWrapper<ItemEntity> last = baseItemIdWrapper(itemId)
            //mysql 和pgsql 都支持
            .last("limit 1");
        return this.getOne(last);
    }

    private LambdaQueryWrapper<ItemEntity> baseItemIdWrapper(String itemId) {
        LambdaQueryWrapper<ItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ItemEntity::getItemId, itemId);
        return wrapper;
    }


}
