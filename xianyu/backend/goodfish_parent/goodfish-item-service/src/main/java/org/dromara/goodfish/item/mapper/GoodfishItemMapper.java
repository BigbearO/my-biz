package org.dromara.goodfish.item.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Param;
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
     * 借助 {@code xmax = 0} 判定插入，再与旧值逐列比对区分"真实更新"与"未变化"，
     * 返回三类计数.</p>
     *
     * <p>主键 id 为应用侧雪花值，实体必须已赋值；依赖唯一索引
     * {@code uk_gfid_item(goodfish_account_id, goodfish_item_id)}.</p>
     *
     * @param entities 商品实体列表（同一账号）
     * @return 新增/更新/未变化计数
     */
    GoodfishItemSyncCountVo upsertBatch(@Param("entities") List<GoodfishItem> entities);

    /**
     * 查询商品详情（联表提示词配置，提示词可为空）.
     */
    GoodfishItemVo selectVoWithPromptById(Long id);

    /**
     * 分页查询商品列表，并标记是否已配置提示词（不加载提示词正文）.
     *
     * @param page    分页参数
     * @param wrapper 查询条件（字段名不加表别名，SQL 中商品表别名为 i 但条件作用于单表）
     * @return 分页结果
     */
    IPage<GoodfishItemVo> selectVoPageWithPromptFlag(IPage<GoodfishItemVo> page,
                                                     @Param(Constants.WRAPPER) Wrapper<GoodfishItem> wrapper);

    /**
     * 按闲鱼账号 ID + 闲鱼商品 ID 查询运行时配置（联表提示词，供 gRPC / 缓存构建）.
     */
    GoodfishItemVo selectRuntimeByGoodfishIds(String goodfishAccountId, String goodfishItemId);

}
