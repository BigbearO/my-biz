package com.bigbear.goodfish.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天议价次数实体
 *
 * @author autoagent
 */
@Data
@TableName("chat_bargain_counts")
public class ChatBargainCount {

    @TableId
    private String chatId;

    private Integer count = 0;

    private LocalDateTime lastUpdated;
}
