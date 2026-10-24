package org.dromara.goodfish.item.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 商品提示词配置对象 goodfish_item_prompt.
 *
 * <p>与 goodfish_item 一对一直接关联；本期页面只编辑 system_prompt 与 max_reply_length，
 * price_prompt / tech_prompt / default_prompt 字段保留但留空（待后续迭代）.</p>
 *
 * @author goodfish
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodfish_item_prompt")
public class GoodfishItemPrompt extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（应用侧雪花 ID；显式声明 ASSIGN_ID，避免全局配置被覆盖后退回自增）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 goodfish_item.id（唯一索引，一对一）
     */
    private Long itemId;

    /**
     * 系统提示词（人设 + 核心原则）
     */
    private String systemPrompt;

    /**
     * 议价场景提示词（本期页面不提供输入，字段保留）
     */
    private String pricePrompt;

    /**
     * 技术场景提示词（本期页面不提供输入，字段保留）
     */
    private String techPrompt;

    /**
     * 默认场景提示词（本期页面不提供输入，字段保留）
     */
    private String defaultPrompt;

    /**
     * 最大回复字数，默认 50
     */
    private Integer maxReplyLength;

}
