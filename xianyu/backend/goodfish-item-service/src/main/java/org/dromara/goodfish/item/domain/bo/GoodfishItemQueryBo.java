package org.dromara.goodfish.item.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品列表查询业务对象（后台筛选条件）.
 *
 * @author goodfish
 */
@Data
public class GoodfishItemQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 闲鱼账号 ID（按账号筛选）
     */
    private String goodfishAccountId;

    /**
     * 商品标题（模糊匹配）
     */
    private String title;

}
