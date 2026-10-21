package org.dromara.goodfish.item.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.goodfish.item.domain.GoodfishItem;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncCountVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;

import java.util.List;

/**
 * 闲鱼商品Mapper接口.
 *
 * @author goodfish
 */
public interface GoodfishItemMapper extends BaseMapperPlus<GoodfishItem, GoodfishItemVo> {

    /**
     * 批量 upsert（单条 CTE 语句，PostgreSQL ON CONFLICT）.
     *
     * <p>已存在商品更新标题/描述/价格/库存状态/同步时间并<strong>保留提示词</strong>（不碰提示词表）；
     * 软删记录命中时 del_flag 置回 0（等同复活）；新商品插入完整记录.
     * 借助 {@code xmax = 0} 判定插入/更新，返回分类计数.</p>
     *
     * @param entities 商品实体列表（同一账号）
     * @return 新增/更新计数
     */
    GoodfishItemSyncCountVo upsertBatch(List<GoodfishItem> entities);

    /**
     * 查询商品详情（联表提示词配置，提示词可为空）.
     */
    GoodfishItemVo selectVoWithPromptById(Long id);

    /**
     * 按闲鱼账号 ID + 闲鱼商品 ID 查询运行时配置（联表提示词，供 gRPC / 缓存构建）.
     */
    GoodfishItemVo selectRuntimeByGoodfishIds(String goodfishAccountId, String goodfishItemId);

}
