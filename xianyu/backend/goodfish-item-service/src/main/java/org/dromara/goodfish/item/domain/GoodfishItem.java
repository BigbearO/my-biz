package org.dromara.goodfish.item.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闲鱼商品对象 goodfish_item.
 *
 * <p>同步只增不删；云端删除为软删，闲鱼侧仍在的商品再次同步会复活（del_flag 置回 0）.</p>
 *
 * @author goodfish
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodfish_item")
public class GoodfishItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（应用侧雪花 ID；显式声明 ASSIGN_ID，避免全局配置被覆盖后退回自增）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联 goodfish_account.id（同步时经 gRPC GetAccountByGoodfishId 解析）
     */
    private Long accountId;

    /**
     * 冗余字段：闲鱼账号 ID（对话链路按此字段直查，不做跨服务解析）
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
     * 商品主图（本期不同步，字段保留、暂不写入）
     */
    private String imageUrl;

    /**
     * 最后同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
