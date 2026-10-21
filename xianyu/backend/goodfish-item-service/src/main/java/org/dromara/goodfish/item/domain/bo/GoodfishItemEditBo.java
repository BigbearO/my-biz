package org.dromara.goodfish.item.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.goodfish.item.domain.GoodfishItem;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品编辑业务对象（商品基本信息 + 提示词配置）.
 *
 * <p>编辑范围：标题 / 描述 / 价格 + 系统提示词 + 最大回复字数；
 * 库存状态只读（从同步来），不在本接口修改；
 * price_prompt / tech_prompt / default_prompt 字段保留，本期页面不提供输入.</p>
 *
 * @author goodfish
 */
@Data
@AutoMapper(target = GoodfishItem.class, reverseConvertGenerate = false)
public class GoodfishItemEditBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品主键
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 价格（字符串透传，避免浮点精度问题，如 199.90）
     */
    private String price;

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
     * 最大回复字数
     */
    private Integer maxReplyLength;

}
