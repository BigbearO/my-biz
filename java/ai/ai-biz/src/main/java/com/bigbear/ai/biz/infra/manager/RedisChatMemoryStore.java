package com.bigbear.ai.biz.infra.manager;

import com.bigbear.ai.biz.dto.SimpleMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.chat.memory.key-prefix:chat:memory:}")
    private String keyPrefix;

    @Value("${spring.ai.chat.memory.redis-ttl-days:7}")
    private long ttlDays;

    /**
     * 获取消息列表（LRANGE 0 -1）
     */
    public List<Message> getMessages(String conversationId) {
        String key = buildKey(conversationId);
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
            if (CollectionUtils.isEmpty(jsonList)) {
                return new ArrayList<>();
            }
            return jsonList.stream()
                    .map(this::deserialize)
                    .filter(Objects::nonNull)
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis getMessages 失败，key: {}", key, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存消息列表（DEL + RPUSHALL + EXPIRE）
     */
    public void saveMessages(String conversationId, List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
            List<String> jsonList = messages.stream()
                    .map(this::toSimpleMessage)
                    .map(this::serialize)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!jsonList.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(key, jsonList);
                redisTemplate.expire(key, Duration.ofDays(ttlDays));
            }
        } catch (Exception e) {
            log.error("Redis saveMessages 失败，key: {}", key, e);
        }
    }

    /**
     * 追加单条消息（RPUSH + LTRIM + EXPIRE）
     */
    public void addMessage(String conversationId, Message message, int maxSize) {
        if (message == null) {
            return;
        }
        String key = buildKey(conversationId);
        try {
            String json = serialize(toSimpleMessage(message));
            if (json == null) {
                return;
            }
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -maxSize, -1);
            redisTemplate.expire(key, Duration.ofDays(ttlDays));
            log.debug("Redis 追加消息成功，conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Redis addMessage 失败，key: {}", key, e);
        }
    }

    /**
     * 删除会话消息（DEL）
     */
    public void deleteMessages(String conversationId) {
        String key = buildKey(conversationId);
        try {
            redisTemplate.delete(key);
            log.debug("Redis 删除会话成功，conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("Redis deleteMessages 失败，key: {}", key, e);
        }
    }

    private String buildKey(String conversationId) {
        return keyPrefix + conversationId;
    }

    private SimpleMessage toSimpleMessage(Message message) {
        String role = message.getMessageType().name().toLowerCase();
        return new SimpleMessage(role, message.getText());
    }

    private Message toMessage(SimpleMessage simple) {
        if (simple == null || simple.getRole() == null) {
            return null;
        }
        return switch (simple.getRole().toLowerCase()) {
            case "user" -> new UserMessage(simple.getContent());
            case "assistant" -> new AssistantMessage(simple.getContent());
            case "system" -> new SystemMessage(simple.getContent());
            default -> {
                log.warn("Unknown role: {}, skipping message", simple.getRole());
                yield null;
            }
        };
    }

    private String serialize(SimpleMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Serialize SimpleMessage 失败", e);
            return null;
        }
    }

    private SimpleMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json, SimpleMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Deserialize SimpleMessage 失败，json: {}", json, e);
            return null;
        }
    }
}
