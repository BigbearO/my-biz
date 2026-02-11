package com.bigbear.mall.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 执行日志表
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("execution_log")
public class ExecutionLogEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 模拟器ID
     */
    private Long emulatorId;

    /**
     * 游戏类型
     */
    private Integer gameType;

    /**
     * 执行命令
     */
    private String command;

    /**
     * 执行状态（0待执行 1执行中 2成功 3失败）
     */
    private Integer status;

    /**
     * MQ消息内容（JSON格式）
     */
    private String mqMessage;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;
}
