package org.dromara.goodfish.item.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.goodfish.item.constants.GoodfishItemConstants;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 商品同步业务对象（客户端「同步商品」全量上报）.
 *
 * <p>同步只增不删：云端按 goodfish_item_id upsert，不删除云端已有商品.</p>
 *
 * @author goodfish
 */
@Data
public class GoodfishItemSyncBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 闲鱼账号 ID（先经 gRPC 解析为云端 account_id，解析不到 → 40403）
     */
    @NotBlank(message = "闲鱼账号ID不能为空")
    @Size(max = GoodfishItemConstants.GOODFISH_ID_MAX_LENGTH, message = "闲鱼账号ID过长")
    private String goodfishAccountId;

    /**
     * 全量商品列表
     */
    @NotEmpty(message = "商品列表不能为空")
    @Size(max = GoodfishItemConstants.SYNC_MAX_ITEMS, message = "单次同步商品数不能超过 500")
    @Valid
    private List<GoodfishItemSyncItemBo> items;

}
