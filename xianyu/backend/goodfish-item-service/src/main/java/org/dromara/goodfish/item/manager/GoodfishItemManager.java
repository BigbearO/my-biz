package org.dromara.goodfish.item.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.GoodfishItem;
import org.dromara.goodfish.item.domain.bo.GoodfishItemSyncItemBo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncCountVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.dromara.goodfish.item.mapper.GoodfishItemMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 闲鱼商品原子操作层（manager）.
 *
 * <p>仅封装可复用的基础数据操作，不做业务编排（AGENTS.md 分层规范）.</p>
 *
 * @author goodfish
 */
@Component
@RequiredArgsConstructor
public class GoodfishItemManager {

    private final GoodfishItemMapper itemMapper;

    /**
     * 批量 upsert（单条 CTE 语句；保留提示词、软删记录复活）.
     *
     * @param accountId         云端账号主键（gRPC 解析得到）
     * @param goodfishAccountId 闲鱼账号 ID（冗余字段）
     * @param items             全量商品上报列表
     * @return 新增/更新计数
     */
    public GoodfishItemSyncCountVo upsertBatch(Long accountId, String goodfishAccountId,
                                               List<GoodfishItemSyncItemBo> items) {
        LocalDateTime syncTime = LocalDateTime.now();
        List<GoodfishItem> entities = items.stream().map(it -> {
            GoodfishItem entity = new GoodfishItem();
            entity.setAccountId(accountId);
            entity.setGoodfishAccountId(goodfishAccountId);
            entity.setGoodfishItemId(it.getGoodfishItemId());
            entity.setTitle(it.getTitle());
            entity.setDescription(it.getDescription());
            entity.setPrice(parsePrice(it.getPrice()));
            entity.setStockStatus(it.getStockStatus());
            entity.setImageUrl(it.getImageUrl());
            entity.setSyncTime(syncTime);
            return entity;
        }).toList();
        return itemMapper.upsertBatch(entities);
    }

    /**
     * 分页查询商品（列表页不联提示词表）.
     */
    public Page<GoodfishItemVo> selectVoPage(Page<GoodfishItem> page, String goodfishAccountId, String title) {
        LambdaQueryWrapper<GoodfishItem> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StringUtils.isNotBlank(goodfishAccountId), GoodfishItem::getGoodfishAccountId, goodfishAccountId)
            .like(StringUtils.isNotBlank(title), GoodfishItem::getTitle, title)
            .orderByDesc(GoodfishItem::getId);
        return itemMapper.selectVoPage(page, lqw);
    }

    /**
     * 查询商品详情（联表提示词配置）.
     */
    public GoodfishItemVo selectVoWithPromptById(Long id) {
        return itemMapper.selectVoWithPromptById(id);
    }

    /**
     * 按闲鱼账号 ID + 闲鱼商品 ID 查询运行时配置（联表提示词）.
     */
    public GoodfishItemVo selectRuntimeByGoodfishIds(String goodfishAccountId, String goodfishItemId) {
        return itemMapper.selectRuntimeByGoodfishIds(goodfishAccountId, goodfishItemId);
    }

    /**
     * 软删商品（@TableLogic 逻辑删除；闲鱼侧仍在的商品再次同步会复活）.
     */
    public boolean softDeleteById(Long id) {
        return itemMapper.deleteById(id) > 0;
    }

    /**
     * 按主键存在性校验（复制提示词目标校验用）.
     */
    public boolean existsById(Long id) {
        return itemMapper.exists(new LambdaQueryWrapper<GoodfishItem>()
            .eq(GoodfishItem::getId, id));
    }

    /**
     * 按主键集合查询视图对象（复制提示词目标定位用）.
     */
    public List<GoodfishItemVo> selectVoByIds(java.util.Collection<Long> ids) {
        return itemMapper.selectVoByIds(ids);
    }

    /**
     * 更新商品基本信息（标题 / 描述 / 价格；库存状态只读不在此更新）.
     */
    public boolean updateBasicById(Long id, String title, String description, String price) {
        GoodfishItem update = new GoodfishItem();
        update.setId(id);
        update.setTitle(title);
        update.setDescription(description);
        update.setPrice(parsePrice(price));
        return itemMapper.updateById(update) > 0;
    }

    /**
     * 按闲鱼账号 ID 统计商品数（未删除）.
     */
    public long countByAccount(String goodfishAccountId) {
        return itemMapper.selectCount(new LambdaQueryWrapper<GoodfishItem>()
            .eq(GoodfishItem::getGoodfishAccountId, goodfishAccountId));
    }

    /**
     * 解析价格：空值按 null 入库，非法格式直接拒绝.
     */
    private BigDecimal parsePrice(String price) {
        if (StringUtils.isBlank(price)) {
            return null;
        }
        try {
            return new BigDecimal(price.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("价格格式不正确: " + price);
        }
    }

    /**
     * 商品不存在校验（详情 / 编辑前置）.
     */
    public GoodfishItemVo requireVoWithPromptById(Long id) {
        GoodfishItemVo vo = selectVoWithPromptById(id);
        if (vo == null) {
            throw new ServiceException(GoodfishItemConstants.MSG_ITEM_NOT_FOUND,
                GoodfishItemConstants.CODE_ITEM_NOT_FOUND);
        }
        return vo;
    }

}
