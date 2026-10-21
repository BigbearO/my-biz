package org.dromara.goodfish.item.manager;

import lombok.RequiredArgsConstructor;
import org.dromara.goodfish.item.domain.GoodfishItemPrompt;
import org.dromara.goodfish.item.mapper.GoodfishItemPromptMapper;
import org.springframework.stereotype.Component;

/**
 * 商品提示词配置原子操作层（manager）.
 *
 * @author goodfish
 */
@Component
@RequiredArgsConstructor
public class GoodfishItemPromptManager {

    private final GoodfishItemPromptMapper promptMapper;

    /**
     * 按 item_id upsert 提示词（不存在插入，存在整组覆盖）.
     */
    public boolean upsertByItemId(GoodfishItemPrompt prompt) {
        return promptMapper.upsertByItemId(prompt) > 0;
    }

    /**
     * 按商品主键查询提示词配置.
     */
    public GoodfishItemPrompt selectByItemId(Long itemId) {
        return promptMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GoodfishItemPrompt>()
            .eq(GoodfishItemPrompt::getItemId, itemId)
            .last("limit 1"));
    }

}
