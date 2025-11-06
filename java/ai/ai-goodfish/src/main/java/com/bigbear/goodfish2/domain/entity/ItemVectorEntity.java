package com.bigbear.goodfish2.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商品向量存储实体类
 */
@Data
@TableName("item_vector")
public class ItemVectorEntity {
    @TableId
    private Long id;
    private String tenantId;
    private String itemId;
    private String vectorData; // 向量数据，JSON格式存储
    private String itemContent; // 原始商品信息内容
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
