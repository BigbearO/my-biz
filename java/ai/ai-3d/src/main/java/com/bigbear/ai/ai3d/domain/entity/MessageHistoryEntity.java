package com.bigbear.ai.ai3d.domain.entity;

import com.bigbear.common.core.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "message_history"  )
public class MessageHistoryEntity extends BaseEntity {

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

//    @Column(isLogicDelete = true,onInsertValue ="" )
    private Integer type;

}
