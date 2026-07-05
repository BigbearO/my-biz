package com.bigbear.goodfish.app;


import com.bigbear.ai.biz.domain.bo.ItemBo;
import com.bigbear.ai.biz.domain.entity.ItemInfo;
import com.bigbear.ai.biz.domain.entity.ItemVectorEntity;
import com.bigbear.ai.biz.domain.vo.ItemVo;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.Collection;
import java.util.List;

/**
 * 商品Service接口
 *
 * @author LionLi
 * @date 2025-10-27
 */
public interface IItemService {

    /**
     * 查询商品
     *
     * @param id 主键
     * @return 商品
     */
    ItemVo queryById(Long id);

    /**
     * 分页查询商品列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 商品分页列表
     */
    TableDataInfo<ItemVo> queryPageList(ItemBo bo, PageQuery pageQuery);

    /**
     * 查询符合条件的商品列表
     *
     * @param bo 查询条件
     * @return 商品列表
     */
    List<ItemVo> queryList(ItemBo bo);

    /**
     * 新增商品
     *
     * @param bo 商品
     * @return 是否新增成功
     */
    Boolean insertByBo(ItemBo bo);

    /**
     * 修改商品
     *
     * @param bo 商品
     * @return 是否修改成功
     */
    Boolean updateByBo(ItemBo bo);

    /**
     * 校验并批量删除商品信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    void vectorizeItem(ItemInfo itemInfo);

    ItemVectorEntity getItemVector(String tenantId, String itemId);
}
