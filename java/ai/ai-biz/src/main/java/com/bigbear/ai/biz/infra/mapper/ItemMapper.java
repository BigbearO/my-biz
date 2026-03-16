package com.bigbear.ai.biz.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.ai.biz.domain.vo.ItemVo;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;


/**
 * 商品Mapper
 *
 * @author autoagent
 */
@Mapper
public interface ItemMapper extends BaseMapper<ItemEntity>, BaseMapperPlus<ItemEntity, ItemVo> {

    /**
     * 根据商品ID查询商品信息
     */
    ItemEntity selectByItemId(String itemId);
}
