package org.dromara.goodfish.item.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.goodfish.item.domain.GoodfishItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闲鱼商品视图对象 goodfish_item（列表 / 详情，含提示词配置）.
 *
 * @author goodfish
 */
@Data
@AutoMapper(target = GoodfishItem.class)
public class GoodfishItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 关联 goodfish_account.id
     */
    private Long accountId;

    /**
     * 闲鱼账号 ID
     */
    private String goodfishAccountId;

    /**
     * 闲鱼商品 ID
     */
    private String goodfishItemId;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 库存状态（只读，从同步来）
     */
    private String stockStatus;

    /**
     * 商品主图
     */
    private String imageUrl;

    /**
     * 最后同步时间
     */
    private LocalDateTime syncTime;

    // ==================== 提示词配置（一对一，详情/编辑回显） ====================

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 议价场景提示词（本期页面不提供输入）
     */
    private String pricePrompt;

    /**
     * 技术场景提示词（本期页面不提供输入）
     */
    private String techPrompt;

    /**
     * 默认场景提示词（本期页面不提供输入）
     */
    private String defaultPrompt;

    /**
     * 最大回复字数
     */
    private Integer maxReplyLength;

    /**
     * 是否已配置提示词（列表页标记；详情页为 null，按 systemPrompt 判断）
     */
    private Boolean promptConfigured;

}
