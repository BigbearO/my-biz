package com.bigbear.ai.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import org.apache.dubbo.common.logger.FluentLogger.S;

/**
 * 商品实体
 *
 * @author autoagent
 */
@Data
@TableName("item")
public class ItemEntity {

    @TableId
    private Long id;

    // 关联的闲鱼平台商品id
    private String itemId;

    private String tenantId;


    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String data;

    private Double price;

    private String description;

    private String title;

    private String category;

    private String location;

    private String imageUrl;

    private String sellerId;

    private String condition;

    private Long publishedDate;

    private String stockStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updateTime;

    private String accountId;

    private String itemInfo;

    private Boolean enable;
}
