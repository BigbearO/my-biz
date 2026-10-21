package org.dromara.goodfish.item.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 商品运行时配置缓存原子操作层（manager，Redis 共享缓存）.
 *
 * <p>提供方负责缓存：TTL 60s 兜底 + 编辑/同步变更时主动删除
 * （多实例共用同一 Redis，任意实例删除即全局生效，调用方无感）.</p>
 *
 * @author goodfish
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemRuntimeCacheManager {

    /**
     * 读取运行时配置缓存.
     */
    public GoodfishItemVo get(String goodfishAccountId, String goodfishItemId) {
        String json = RedisUtils.getCacheObject(buildKey(goodfishAccountId, goodfishItemId));
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, GoodfishItemVo.class);
        } catch (Exception e) {
            // 缓存脏数据不阻断业务：删除后回源数据库
            log.warn("[{}] 运行时配置缓存解析失败，删除并回源 accountId={}, itemId={}",
                GoodfishItemConstants.LOGGER_ITEM_SERVICE, goodfishAccountId, goodfishItemId, e);
            delete(goodfishAccountId, goodfishItemId);
            return null;
        }
    }

    /**
     * 写入运行时配置缓存（TTL 60s）.
     */
    public void set(GoodfishItemVo vo) {
        RedisUtils.setCacheObject(buildKey(vo.getGoodfishAccountId(), vo.getGoodfishItemId()),
            JsonUtils.toJsonString(vo), GoodfishItemConstants.ITEM_RUNTIME_CACHE_TTL);
    }

    /**
     * 删除单条运行时配置缓存（编辑 / 删除商品时调用）.
     */
    public void delete(String goodfishAccountId, String goodfishItemId) {
        RedisUtils.deleteObject(buildKey(goodfishAccountId, goodfishItemId));
    }

    /**
     * 批量删除运行时配置缓存（同步 / 复制提示词后调用）.
     */
    public void deleteAll(String goodfishAccountId, Collection<String> goodfishItemIds) {
        if (goodfishItemIds == null || goodfishItemIds.isEmpty()) {
            return;
        }
        goodfishItemIds.forEach(itemId -> delete(goodfishAccountId, itemId));
    }

    /**
     * 组装缓存 key：goodfish:item:runtime:{闲鱼账号ID}:{闲鱼商品ID}.
     */
    private String buildKey(String goodfishAccountId, String goodfishItemId) {
        return GoodfishItemConstants.ITEM_RUNTIME_CACHE_KEY_PREFIX + goodfishAccountId + ":" + goodfishItemId;
    }

}
