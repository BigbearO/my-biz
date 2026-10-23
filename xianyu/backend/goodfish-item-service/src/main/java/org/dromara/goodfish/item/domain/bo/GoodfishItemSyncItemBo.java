package org.dromara.goodfish.item.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;

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
    @Size(max = GoodfishItemConstants.GOODFISH_ID_MAX_LENGTH, message = "闲鱼商品ID过长")
    private String goodfishItemId;

    /**
     * 商品标题
     */
    @Size(max = GoodfishItemConstants.TITLE_MAX_LENGTH, message = "商品标题过长")
    private String title;

    /**
     * 商品描述
     */
    @Size(max = GoodfishItemConstants.DESCRIPTION_MAX_LENGTH, message = "商品描述过长")
    private String description;

    /**
     * 价格（字符串透传，避免浮点精度问题，如 199.90）
     */
    @Pattern(regexp = GoodfishItemConstants.PRICE_PATTERN, message = GoodfishItemConstants.MSG_PRICE_INVALID)
    private String price;

    /**
     * 库存状态（只读，从同步来）
     */
    @Size(max = GoodfishItemConstants.GOODFISH_ID_MAX_LENGTH, message = "库存状态过长")
    private String stockStatus;

    /**
     * 商品主图（本期不同步，字段保留、暂不写入）
     */
    @Size(max = GoodfishItemConstants.IMAGE_URL_MAX_LENGTH, message = "图片地址过长")
    private String imageUrl;

}
