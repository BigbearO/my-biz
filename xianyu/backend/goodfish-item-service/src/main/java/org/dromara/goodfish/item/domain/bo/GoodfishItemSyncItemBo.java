package org.dromara.goodfish.item.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品同步明细业务对象（单条商品上报）.
 *
 * @author goodfish
 */
@Data
public class GoodfishItemSyncItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 闲鱼商品 ID
     */
    @NotBlank(message = "闲鱼商品ID不能为空")
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
     * 价格（字符串透传，避免浮点精度问题，如 199.90）
     */
    private String price;

    /**
     * 库存状态（只读，从同步来）
     */
    private String stockStatus;

    /**
     * 商品主图（本期不同步，字段保留、暂不写入）
     */
    private String imageUrl;

}
