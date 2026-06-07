package com.bigbear.ai.ai3d.infra.memory;

import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.bigbear.ai.ai3d.domain.entity.CheckpointHistoryEntity;
import com.bigbear.ai.ai3d.infra.mapper.CheckpointHistoryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PgCheckpointStore {

    private final CheckpointHistoryMapper checkpointHistoryMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveCheckpoint(String threadId, Checkpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        CheckpointHistoryEntity entity = new CheckpointHistoryEntity()
                .setThreadId(threadId)
                .setCheckpointId(checkpoint.getId())
                .setNodeId(checkpoint.getNodeId())
                .setNextNodeId(checkpoint.getNextNodeId())
                .setStateData(serializeState(checkpoint.getState()))
                .setCreateTime(LocalDateTime.now());
        try {
            checkpointHistoryMapper.insert(entity);
        } catch (Exception e) {
            log.error("PgCheckpointStore saveCheckpoint 失败, threadId: {}", threadId, e);
            throw e;
        }
    }

    public List<Checkpoint> findRecentCheckpoints(String threadId, int limit) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(CheckpointHistoryEntity.class)
                .where(CheckpointHistoryEntity::getThreadId).eq(threadId)
                .orderBy(CheckpointHistoryEntity::getCreateTime, false)
                .limit(limit);
        try {
            List<Checkpoint> result = checkpointHistoryMapper.selectListByQuery(wrapper)
                    .stream()
                    .map(this::toCheckpoint)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Collections.reverse(result);
            return result;
        } catch (Exception e) {
            log.error("PgCheckpointStore findRecentCheckpoints 失败, threadId: {}", threadId, e);
            return Collections.emptyList();
        }
    }

    private String serializeState(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            log.error("Serialize state 失败", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Checkpoint toCheckpoint(CheckpointHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> state;
        try {
            state = objectMapper.readValue(entity.getStateData(), Map.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize state 失败, checkpointId: {}", entity.getCheckpointId(), e);
            return null;
        }
        return Checkpoint.builder()
                .id(entity.getCheckpointId())
                .state(state)
                .nodeId(entity.getNodeId())
                .nextNodeId(entity.getNextNodeId())
                .build();
    }
}
