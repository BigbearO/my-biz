package com.bigbear.ai.ai3d.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PreConsultPhaseTracker {

    private static final List<String> PHASE_ORDER = List.of(
        "GREETING", "CHIEF_COMPLAINT", "HPI", "MEDICAL_HISTORY", "GOAL", "SUMMARY"
    );

    private static final String REDIS_KEY_PREFIX = "pre:consult:state:";
    private static final long REDIS_TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pre-consult.max-rounds:15}")
    private int maxRounds;

    public PreConsultPhaseTracker(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PhaseState getState(String conversationId) {
        String key = buildKey(conversationId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return new PhaseState("GREETING", 0, LocalDateTime.now());
        }
        try {
            return objectMapper.readValue(json, PhaseState.class);
        } catch (JsonProcessingException e) {
            log.warn("解析阶段状态失败, key={}", key, e);
            return new PhaseState("GREETING", 0, LocalDateTime.now());
        }
    }

    public void saveState(String conversationId, PhaseState state) {
        String key = buildKey(conversationId);
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(REDIS_TTL_HOURS));
        } catch (JsonProcessingException e) {
            log.warn("序列化阶段状态失败, key={}", key, e);
        }
    }

    public String getCurrentPhase(String conversationId) {
        return getState(conversationId).getPhase();
    }

    public int getRoundCount(String conversationId) {
        return getState(conversationId).getRounds();
    }

    public int incrementRound(String conversationId) {
        PhaseState state = getState(conversationId);
        state.setRounds(state.getRounds() + 1);
        saveState(conversationId, state);
        return state.getRounds();
    }

    public boolean isValidTransition(String conversationId, String newPhase) {
        PhaseState state = getState(conversationId);
        String currentPhase = state.getPhase();
        int currentIdx = PHASE_ORDER.indexOf(currentPhase);
        int newIdx = PHASE_ORDER.indexOf(newPhase);
        if (currentIdx == -1 || newIdx == -1) {
            return false;
        }
        return newIdx >= currentIdx;
    }

    public boolean advancePhase(String conversationId, String newPhase) {
        if (!isValidTransition(conversationId, newPhase)) {
            log.warn("阶段推进非法: conversationId={}, currentPhase={}, newPhase={}",
                conversationId, getCurrentPhase(conversationId), newPhase);
            return false;
        }
        PhaseState state = getState(conversationId);
        state.setPhase(newPhase);
        saveState(conversationId, state);
        return true;
    }

    public boolean isApproachingLimit(String conversationId) {
        return getRoundCount(conversationId) >= (maxRounds - 3);
    }

    public boolean isAtLimit(String conversationId) {
        return getRoundCount(conversationId) >= maxRounds;
    }

    public void clear(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId) {
        return REDIS_KEY_PREFIX + conversationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseState {
        private String phase = "GREETING";
        private int rounds = 0;
        private LocalDateTime startedAt = LocalDateTime.now();
    }
}
