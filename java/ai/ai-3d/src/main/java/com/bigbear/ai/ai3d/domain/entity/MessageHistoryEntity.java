package com.bigbear.ai.ai3d.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Table("message_history")
@Accessors(chain=true)
public class MessageHistoryEntity {

    @Id(keyType = KeyType.Generator, value= KeyGenerators.flexId)
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

    private Integer type;

}
