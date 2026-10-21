package org.dromara.goodfish.admin.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理后台商品列表视图对象（商品 + 账号聚合）.
 *
 * <p>账号名/昵称由 BFF 内存拼装（不跨库 JOIN）：accountRemark 为运营备注、
 * accountNickname 为闲鱼昵称，「所属账号」列由前端优先取 remark.</p>
 *
 * @author goodfish
 */
@Data
public class AdminItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品主键（云端）
     */
    private Long id;

    /**
     * 闲鱼账号 ID（BFF 聚合账号名的关联键）
     */
    private String goodfishAccountId;

    /**
     * 账号备注（运营视角的账号名，可空）
     */
    private String accountRemark;

    /**
     * 闲鱼昵称（账号 remark 为空时前端可回落展示）
     */
    private String accountNickname;

    /**
     * 闲鱼商品 ID
     */
    private String goodfishItemId;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 价格（字符串透传，避免浮点精度问题）
     */
    private String price;

    /**
     * 库存状态（只读，从同步来）
     */
    private String stockStatus;

    /**
     * 最后同步时间
     */
    private String lastSyncAt;

}
