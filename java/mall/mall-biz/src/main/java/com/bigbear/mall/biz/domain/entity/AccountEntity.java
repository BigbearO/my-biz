package com.bigbear.mall.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 游戏账号表
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("game_account")
@Accessors(chain = true)
public class AccountEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 订单表主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户登录凭证
     */
    private String credential;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 游戏类型
     */
    private Integer type;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 分配的模拟器ID
     */
    private Long emulatorId;

    /**
     * 多租户id
     */
    private Long tenantId;
}
