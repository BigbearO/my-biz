package org.dromara.goodfish.item.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.goodfish.item.domain.GoodfishItemPrompt;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;

/**
 * 商品提示词配置Mapper接口.
 *
 * @author goodfish
 */
public interface GoodfishItemPromptMapper extends BaseMapperPlus<GoodfishItemPrompt, GoodfishItemVo> {

    /**
     * 按 item_id upsert（PostgreSQL ON CONFLICT，一对一）.
     *
     * <p>编辑 / 复制提示词均走此方法：提示词行不存在则插入，存在则整组覆盖.</p>
     *
     * @param prompt 提示词实体
     * @return 影响行数
     */
    int upsertByItemId(GoodfishItemPrompt prompt);

}
