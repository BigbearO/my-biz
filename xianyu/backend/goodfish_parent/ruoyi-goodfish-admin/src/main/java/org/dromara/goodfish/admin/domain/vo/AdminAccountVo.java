package org.dromara.goodfish.admin.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理后台账号视图对象（停用/启用转调返回值）.
 *
 * @author goodfish
 */
@Data
public class AdminAccountVo implements Serializable {

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
    private String lastActiveAt;

}
