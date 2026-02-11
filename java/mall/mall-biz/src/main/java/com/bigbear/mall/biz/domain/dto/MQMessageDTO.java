package com.bigbear.mall.biz.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQ消息DTO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MQMessageDTO {

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 执行命令
     */
    private String command;

    /**
     * 模拟器IP
     */
    private String emulatorIp;

    /**
     * 模拟器端口
     */
    private String emulatorPort;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 账号ID（扩展字段）
     */
    private Long accountId;
}
