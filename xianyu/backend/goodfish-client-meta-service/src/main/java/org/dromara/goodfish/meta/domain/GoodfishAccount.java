package org.dromara.goodfish.meta.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 闲鱼账号对象 goodfish_account.
 *
 * <p>云端不存储 Cookie；账号只停用、不删除（无逻辑删除字段）.</p>
 *
 * @author goodfish
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goodfish_account")
public class GoodfishAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（应用侧雪花 ID；显式声明 ASSIGN_ID，避免全局配置被覆盖后退回自增）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 闲鱼用户 ID（唯一）
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
     * 账号状态：normal / disabled（★只停用、不删除）
     */
    private String status;

    /**
     * 最后活跃时间（客户端上报时由本服务维护）
     */
    private LocalDateTime lastActiveAt;

}
