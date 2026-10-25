package org.dromara.goodfish.item.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品同步结果视图对象.
 *
 * @author goodfish
 */
@Data
public class GoodfishItemSyncVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新增商品数
     */
    private int inserted;

    /**
     * 更新商品数（含软删记录复活）
     */
    private int updated;

}
