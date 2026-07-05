package com.bigbear.ai.biz.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品向量存储实体类
 */
@Data
@Table("item_vector")
public class ItemVectorEntity {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private Long id;
    private String tenantId;
    private String itemId;
    private String vectorData; // 向量数据，JSON格式存储
    private String itemContent; // 原始商品信息内容
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
