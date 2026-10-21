package org.dromara.goodfish.item.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品批量 upsert 计数结果（xmax = 0 判定插入，其余为更新/复活）.
 *
 * @author goodfish
 */
@Data
public class GoodfishItemSyncCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新增数
     */
    private int insertedCount;

    /**
     * 更新数（含软删记录复活）
     */
    private int updatedCount;

}
