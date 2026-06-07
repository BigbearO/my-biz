package com.bigbear.ai.ai3d.domain.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Table(value = "checkpoint_history")
public class CheckpointHistoryEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.flexId)
    private Long id;

    private String threadId;

    private String checkpointId;

    private String nodeId;

    private String nextNodeId;

    private String stateData;

    private LocalDateTime createTime;
}
