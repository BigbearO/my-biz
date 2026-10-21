package org.dromara.goodfish.item.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.goodfish.item.domain.bo.GoodfishItemCopyPromptBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemEditBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemQueryBo;
import org.dromara.goodfish.item.domain.bo.GoodfishItemSyncBo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemSyncVo;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;

/**
 * 闲鱼商品Service接口（商品同步落库 + 查询 + 提示词配置）.
 *
 * @author goodfish
 */
public interface IGoodfishItemService {

    /**
     * 商品同步（客户端全量上报，upsert 落库）.
     *
     * <p>先经 gRPC 解析账号（不存在 → 40403）→ 校验停用（→ 40404）→ 批量 upsert；
     * 只增不删、保留已有提示词、软删记录复活；账号校验失败整批回滚不做部分落库.</p>
     *
     * @param bo 同步参数（闲鱼账号 ID + 全量商品列表）
     * @return 新增/更新计数
     */
    GoodfishItemSyncVo syncItems(GoodfishItemSyncBo bo);

    /**
     * 分页查询商品列表（后台）.
     */
    PageResult<GoodfishItemVo> queryPageList(GoodfishItemQueryBo bo, PageQuery pageQuery);

    /**
     * 查询商品详情（含提示词配置，后台编辑回显）.
     */
    GoodfishItemVo queryById(Long id);

    /**
     * 更新商品 + 提示词配置（后台编辑）.
     *
     * <p>库存状态只读不同步更新；提示词与商品一对一 upsert.</p>
     */
    Boolean editItem(GoodfishItemEditBo bo);

    /**
     * 删除商品（软删；闲鱼侧仍在的商品再次同步会复活）.
     */
    Boolean removeItem(Long id);

    /**
     * 复制提示词到其他商品（覆盖式；复制后独立可编辑）.
     */
    Boolean copyPrompt(GoodfishItemCopyPromptBo bo);

    /**
     * 按闲鱼账号 ID 统计商品数（后台统计展示）.
     */
    long countByAccount(String goodfishAccountId);

}
