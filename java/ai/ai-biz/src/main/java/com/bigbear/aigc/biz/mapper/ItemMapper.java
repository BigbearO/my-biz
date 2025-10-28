package com.bigbear.aigc.biz.mapper;

import com.bigbear.aigc.biz.domain.entity.ItemEntity;
import com.bigbear.aigc.biz.domain.vo.ItemVo;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface ItemMapper extends MPJBaseMapper<ItemEntity>, BaseMapperPlus<ItemEntity, ItemVo> {

    /**
     * 根据商品ID查询商品信息
     */
    ItemEntity selectByItemId(String itemId);
}
