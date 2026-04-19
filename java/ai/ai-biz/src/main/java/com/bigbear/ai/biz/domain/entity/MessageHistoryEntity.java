package com.bigbear.ai.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 消息实体
 */
@Data
@TableName("message_history")
@Accessors(chain=true)
public class MessageHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userId;

    private String itemId;

    private String role;

    private String content;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;

    private String chatId;

    private Long tenantId;

    private String fromUserId;

    // todo 转string
    private Integer type;

}
