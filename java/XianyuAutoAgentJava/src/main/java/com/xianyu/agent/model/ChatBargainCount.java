package com.xianyu.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_bargain_counts")
public class ChatBargainCount {
    @TableId(type = IdType.INPUT)
    private String chatId;
    private Integer count;
    private LocalDateTime lastUpdated;
}
