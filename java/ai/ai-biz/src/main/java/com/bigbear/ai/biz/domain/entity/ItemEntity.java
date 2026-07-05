package com.bigbear.ai.biz.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商品实体
 *
 * @author autoagent
 */
@Data
@Table("item")
public class ItemEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private Long id;

    // 关联的闲鱼平台商品id
    private String itemId;

    private String tenantId;

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
