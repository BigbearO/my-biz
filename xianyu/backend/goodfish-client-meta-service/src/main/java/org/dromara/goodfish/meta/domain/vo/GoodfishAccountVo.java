package org.dromara.goodfish.meta.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.goodfish.meta.domain.GoodfishAccount;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 闲鱼账号视图对象 goodfish_account.
 *
 * @author goodfish
 */
@Data
@AutoMapper(target = GoodfishAccount.class)
public class GoodfishAccountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 闲鱼用户 ID
     */
    private String goodfishId;

    /**
     * 闲鱼昵称
     */
    private String goodfishNickname;

    /**
     * 备注
     */
    private String remark;

    /**
     * 账号状态：normal / disabled
     */
    private String status;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

}
