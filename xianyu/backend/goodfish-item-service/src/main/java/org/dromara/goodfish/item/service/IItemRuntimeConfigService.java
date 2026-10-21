package org.dromara.goodfish.item.service;

import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;

import java.util.List;

/**
 * 商品运行时配置Service接口（供对话服务 gRPC 查询）.
 *
 * <p>缓存由本服务（提供方）负责：Redis 共享缓存，TTL 60s 兜底 + 变更时主动删除，
 * 调用方（对话服务）无感，不做任何缓存.</p>
 *
 * @author goodfish
 */
public interface IItemRuntimeConfigService {

    /**
     * 查询单商品运行时配置.
     *
     * @param goodfishAccountId 闲鱼账号 ID（商品按账号隔离）
     * @param goodfishItemId    闲鱼商品 ID
     * @return 运行时配置；商品不存在 / 已删除返回 null
     */
    GoodfishItemVo getRuntimeConfig(String goodfishAccountId, String goodfishItemId);

    /**
     * 批量查询运行时配置（不存在的商品直接跳过，不占位）.
     */
    List<GoodfishItemVo> batchGetRuntimeConfig(String goodfishAccountId, List<String> goodfishItemIds);

}
