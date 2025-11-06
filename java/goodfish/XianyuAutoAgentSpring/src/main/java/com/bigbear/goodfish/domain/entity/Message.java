package com.bigbear.goodfish.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息实体
 *
 * @author autoagent
 */
@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private Long itemId;

    private String role;

    private String content;

    private LocalDateTime timestamp;

    private String chatId;

    private Long tenantId;

    private String fromUserId;

}
