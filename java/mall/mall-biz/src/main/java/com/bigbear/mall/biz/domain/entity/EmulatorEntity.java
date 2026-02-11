package com.bigbear.mall.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 模拟器表
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emulator")
public class EmulatorEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模拟器名称
     */
    private String name;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 游戏类型（0=原神, 1=尘白禁区）
     */
    private Integer gameType;

    /**
     * 数量上限（该模拟器最多承载账号数）
     */
    private Integer maxCount;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
