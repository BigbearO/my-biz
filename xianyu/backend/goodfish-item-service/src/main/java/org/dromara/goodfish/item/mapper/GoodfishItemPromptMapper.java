package org.dromara.goodfish.item.mapper;

import org.apache.ibatis.annotations.Param;
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
     * <p>行不存在则插入（最大回复字数回落默认值），存在则按「未传不改」合并：
     * 传入字段为 null 时保留库中原值，避免前端只提交部分字段时把提示词清空.</p>
     *
     * <p>主键 id 为应用侧雪花值（MyBatis-Plus ASSIGN_ID），原生 SQL 必须显式写入；
     * 依赖唯一索引 {@code uk_item_id(item_id)}.</p>
     *
     * @param prompt                  提示词实体
     * @param defaultMaxReplyLength   插入新行时的默认最大回复字数
     * @return 影响行数
     */
    int upsertByItemId(@Param("prompt") GoodfishItemPrompt prompt,
                       @Param("defaultMaxReplyLength") int defaultMaxReplyLength);

}
