package org.dromara.goodfish.item.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;
import org.dromara.goodfish.item.domain.GoodfishItemPrompt;
import org.dromara.goodfish.item.mapper.GoodfishItemPromptMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 商品提示词配置原子操作层（manager）.
 *
 * <p>仅封装可复用的基础数据操作，不做业务编排（AGENTS.md 分层规范）.</p>
 *
 * @author goodfish
 */
@Component
@RequiredArgsConstructor
public class GoodfishItemPromptManager {

    private final GoodfishItemPromptMapper promptMapper;

    /**
     * 保存提示词：不存在则插入，存在则按主键更新.
     *
     * <p>「未传不改」由 MyBatis-Plus 的 {@code updateStrategy: NOT_NULL} 保证——
     * 入参为 null 的字段不进 UPDATE 语句，库中原值保留（避免只改标题把提示词清空，
     * 导致对话链路返回 40402）.</p>
     *
     * <p>并发兜底：同一商品并发保存时，后到的一方可能撞唯一索引
     * {@code uk_goodfish_item_prompt_item_id}，此处退化为按主键更新，不向上抛错.</p>
     *
     * @param prompt 提示词实体（itemId 必填，id 由本方法决定）
     * @return 是否成功
     */
    public boolean saveByItemId(GoodfishItemPrompt prompt) {
        GoodfishItemPrompt existing = selectByItemId(prompt.getItemId());
        if (existing == null) {
            Integer originalMaxReplyLength = prompt.getMaxReplyLength();
            if (originalMaxReplyLength == null) {
                prompt.setMaxReplyLength(GoodfishItemConstants.DEFAULT_MAX_REPLY_LENGTH);
            }
            try {
                return promptMapper.insert(prompt) > 0;
            } catch (DuplicateKeyException e) {
                existing = selectByItemId(prompt.getItemId());
                if (existing == null) {
                    throw e;
                }
                // 并发下已被他人插入：还原未被显式提交的字段，走更新分支
                prompt.setMaxReplyLength(originalMaxReplyLength);
            }
        }
        prompt.setId(existing.getId());
        return promptMapper.updateById(prompt) > 0;
    }

    /**
     * 按商品主键查询提示词配置.
     */
    public GoodfishItemPrompt selectByItemId(Long itemId) {
        return promptMapper.selectOne(new LambdaQueryWrapper<GoodfishItemPrompt>()
            .eq(GoodfishItemPrompt::getItemId, itemId)
            .last("limit 1"));
    }

}
