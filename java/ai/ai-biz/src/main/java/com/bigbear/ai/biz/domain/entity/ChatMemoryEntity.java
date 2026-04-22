package com.bigbear.ai.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import lombok.experimental.Accessors;

/**
 * 对话记忆存储实体类
 */
@Data
@TableName("chat_memory")
@Accessors(chain = true)
public class ChatMemoryEntity {

    @TableId
    private Long id;
    private String tenantId;
    private String memoryId; // 商品id和对话id组合
    private String itemId;
    private String conversationId;
    private String messages; // JSON格式的对话消息
    private Integer messageCount; // 消息数量
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
