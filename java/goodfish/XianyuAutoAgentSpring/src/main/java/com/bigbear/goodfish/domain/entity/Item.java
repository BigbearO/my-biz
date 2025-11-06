package com.bigbear.goodfish.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商品实体
 *
 * @author autoagent
 */
@Data
@TableName("items")
public class Item {

    @TableId
    private Long id;

    // 关联的闲鱼平台商品id
    private String itemId;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
