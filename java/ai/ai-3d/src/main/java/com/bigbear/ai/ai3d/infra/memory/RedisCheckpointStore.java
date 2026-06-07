package com.bigbear.ai.ai3d.infra.memory;

import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCheckpointStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.agent.checkpoint.key-prefix:agent:checkpoint:}")
    private String keyPrefix;

    @Value("${spring.ai.agent.checkpoint.redis-ttl-days:7}")
    private long ttlDays;

    @Value("${spring.ai.agent.checkpoint.max-size:20}")
    private int maxSize;

    public List<Checkpoint> getCheckpoints(String threadId) {
        String key = buildKey(threadId);
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (CollectionUtils.isEmpty(jsonList)) {
                return new ArrayList<>();
            }
            return jsonList.stream()
                    .map(this::deserialize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis getCheckpoints 失败, threadId: {}", threadId, e);
            return new ArrayList<>();
        }
    }

    public void saveCheckpoints(String threadId, Collection<Checkpoint> checkpoints) {
        if (CollectionUtils.isEmpty(checkpoints)) {
            return;
        }
        String key = buildKey(threadId);
        try {
            redisTemplate.delete(key);
            List<String> jsonList = checkpoints.stream()
                    .map(this::serialize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!jsonList.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(key, jsonList);
                redisTemplate.expire(key, Duration.ofDays(ttlDays));
            }
        } catch (Exception e) {
            log.error("Redis saveCheckpoints 失败, threadId: {}", threadId, e);
        }
    }

    public void addCheckpoint(String threadId, Checkpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        String key = buildKey(threadId);
        try {
            String json = serialize(checkpoint);
            if (json == null) {
                return;
            }
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.error("Redis addCheckpoint 失败, threadId: {}", threadId, e);
        }
    }

    public void deleteCheckpoints(String threadId) {
        String key = buildKey(threadId);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis deleteCheckpoints 失败, threadId: {}", threadId, e);
        }
    }

    private String buildKey(String threadId) {
        return keyPrefix + threadId;
    }

    private String serialize(Checkpoint checkpoint) {
        try {
            return objectMapper.writeValueAsString(checkpoint);
        } catch (JsonProcessingException e) {
            log.error("Serialize Checkpoint 失败", e);
            return null;
        }
    }

    private Checkpoint deserialize(String json) {
        try {
            return objectMapper.readValue(json, Checkpoint.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize Checkpoint 失败", e);
            return null;
        }
    }
}
