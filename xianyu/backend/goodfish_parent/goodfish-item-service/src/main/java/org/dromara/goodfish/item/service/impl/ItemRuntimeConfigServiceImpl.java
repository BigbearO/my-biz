package org.dromara.goodfish.item.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.dromara.goodfish.item.manager.GoodfishItemManager;
import org.dromara.goodfish.item.manager.ItemRuntimeCacheManager;
import org.dromara.goodfish.item.service.IItemRuntimeConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品运行时配置Service业务层实现.
 *
 * <p>查询路径：Redis 缓存 → 未命中回源数据库（联表提示词）→ 回填缓存（TTL 60s）.</p>
 *
 * @author goodfish
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemRuntimeConfigServiceImpl implements IItemRuntimeConfigService {

    private final GoodfishItemManager itemManager;
    private final ItemRuntimeCacheManager runtimeCacheManager;

    /**
     * 查询单商品运行时配置.
     */
    @Override
    public GoodfishItemVo getRuntimeConfig(String goodfishAccountId, String goodfishItemId) {
        if (StringUtils.isBlank(goodfishAccountId) || StringUtils.isBlank(goodfishItemId)) {
            return null;
        }
        GoodfishItemVo cached = runtimeCacheManager.get(goodfishAccountId, goodfishItemId);
        if (cached != null) {
            return cached;
        }
        GoodfishItemVo vo = itemManager.selectRuntimeByGoodfishIds(goodfishAccountId, goodfishItemId);
        if (vo != null) {
            runtimeCacheManager.set(vo);
        }
        return vo;
    }

    /**
     * 批量查询运行时配置.
     */
    @Override
    public List<GoodfishItemVo> batchGetRuntimeConfig(String goodfishAccountId, List<String> goodfishItemIds) {
        List<GoodfishItemVo> result = new ArrayList<>();
        if (StringUtils.isBlank(goodfishAccountId) || goodfishItemIds == null || goodfishItemIds.isEmpty()) {
            return result;
        }
        for (String goodfishItemId : goodfishItemIds) {
            GoodfishItemVo vo = getRuntimeConfig(goodfishAccountId, goodfishItemId);
            if (vo != null) {
                result.add(vo);
            }
        }
        log.debug("[{}] 批量运行时配置查询 accountId={}, 请求={}, 命中={}",
            GoodfishItemConstants.LOGGER_ITEM_SERVICE, goodfishAccountId,
            goodfishItemIds.size(), result.size());
        return result;
    }

}
