package org.dromara.goodfish.item.manager;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
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
     * 按 item_id upsert 提示词（不存在插入，存在未传不改）.
     *
     * <p>主键为雪花 ID：原生 SQL 不会经过 MyBatis-Plus 的 ID 填充，必须在此显式赋值。</p>
     */
    public boolean upsertByItemId(GoodfishItemPrompt prompt) {
        if (prompt.getId() == null) {
            prompt.setId(IdWorker.getId());
        }
        return promptMapper.upsertByItemId(prompt, GoodfishItemConstants.DEFAULT_MAX_REPLY_LENGTH) > 0;
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
