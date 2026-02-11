package com.bigbear.mall.biz.domain.vo;

import lombok.Data;

/**
 * 可用模拟器VO
 *
 * @author bigbear
 * @date 2025-02-09
 */
@Data
public class AvailableEmulatorVO {

    /**
     * 模拟器ID
     */
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
     * 游戏类型
     */
    private Integer gameType;

    /**
     * 数量上限
     */
    private Integer maxCount;

    /**
     * 当前已分配账号数
     */
    private Long currentCount;

    /**
     * 剩余可用数量
     */
    private Long availableCount;
}
