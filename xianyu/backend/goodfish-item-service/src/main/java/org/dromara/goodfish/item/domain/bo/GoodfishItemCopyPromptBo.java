package org.dromara.goodfish.item.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 复制提示词业务对象（覆盖式：将源商品的提示词内容复制到目标商品）.
 *
 * <p>复制后目标商品的提示词可独立编辑，与源商品、模板均不再关联.</p>
 *
 * @author goodfish
 */
@Data
public class GoodfishItemCopyPromptBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源商品主键
     */
    @NotNull(message = "源商品不能为空")
    private Long sourceItemId;

    /**
     * 目标商品主键集合（可多选）
     */
    @NotEmpty(message = "目标商品不能为空")
    @Size(max = GoodfishItemConstants.SYNC_MAX_ITEMS, message = "单次复制目标商品数不能超过 500")
    private List<Long> targetItemIds;

}
