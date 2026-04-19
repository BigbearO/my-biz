package com.bigbear.ai.biz.infra.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 基于 Redis List 的 ChatMemoryRepository 实现 使用 Redis List 存储滑动窗口消息，支持 RPUSH/LRANGE/LTRIM 操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key 前缀
    private static final String KEY_PREFIX = "chat:memory:";
    // 默认过期时间：7天
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    @Override
    public List<String> findConversationIds() {
        // 扫描所有匹配的 key
        String pattern = KEY_PREFIX + "*";
        return redisTemplate.keys(pattern).stream()
            .map(key -> key.replace(KEY_PREFIX, ""))
            .collect(Collectors.toList());
    }

    /**
     * 根据会话 ID 查询消息列表 使用 LRANGE key 0 -1 读取全部消息
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);

        // LRANGE key 0 -1：读取全部元素
        List<Object> jsonList = redisTemplate.opsForList().range(key, 0, -1);

        if (CollectionUtils.isEmpty(jsonList)) {
            log.debug("Redis 中未找到会话记录，conversationId={}", conversationId);
            return new ArrayList<>();
        }

        return jsonList.stream()
            .map(Object::toString)
            .map(this::deserializeMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 保存消息列表（覆盖式） 使用 DEL + RPUSHALL 模式
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            log.warn("消息列表为空，跳过保存，conversationId={}", conversationId);
            return;
        }

        String key = buildKey(conversationId);

        try {
            // 1. 删除旧数据
            redisTemplate.delete(key);

            // 2. 序列化消息列表
            List<String> jsonList = messages.stream()
                .map(this::serializeMessage)
                .filter(Objects::nonNull)
                .toList();

            if (!jsonList.isEmpty()) {
                // 3. 批量追加到 List
                redisTemplate.opsForList().rightPushAll(key, jsonList);
                // 4. 设置过期时间
                redisTemplate.expire(key, DEFAULT_TTL);
            }

            log.debug("保存对话记忆成功，conversationId={}，消息数={}", conversationId, messages.size());
        } catch (Exception e) {
            log.error("保存对话记忆失败，conversationId={}", conversationId, e);
            throw new RuntimeException("保存对话记忆失败", e);
        }
    }

    /**
     * 根据会话 ID 删除消息
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        redisTemplate.delete(key);
        log.debug("删除对话记忆成功，conversationId={}", conversationId);
    }

    /**
     * 使用 LTRIM 裁剪列表到指定大小 保留最后 maxSize 条消息（使用负数索引）
     */
    public void trimToSize(String conversationId, int maxSize) {
        String key = buildKey(conversationId);
        // LTRIM key -maxSize -1：保留最后 maxSize 条
        redisTemplate.opsForList().trim(key, -maxSize, -1);
        log.debug("裁剪对话记忆成功，conversationId={}，保留条数={}", conversationId, maxSize);
    }

    /**
     * 增量追加单条消息（可选优化） 使用 RPUSH + LTRIM 实现滑动窗口
     */
    public void addMessage(String conversationId, Message message, int maxSize) {
        String key = buildKey(conversationId);

        try {
            String json = serializeMessage(message);
            if (json == null) {
                return;
            }

            // RPUSH：右侧追加
            redisTemplate.opsForList().rightPush(key, json);

            // LTRIM：裁剪窗口，保留最后 maxSize 条
            redisTemplate.opsForList().trim(key, -maxSize, -1);

            // 续期 TTL
            redisTemplate.expire(key, DEFAULT_TTL);

            log.debug("追加消息成功，conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("追加消息失败，conversationId={}", conversationId, e);
            throw new RuntimeException("追加消息失败", e);
        }
    }

    /**
     * 获取列表长度
     */
    public Long getMessageCount(String conversationId) {
        String key = buildKey(conversationId);
        return redisTemplate.opsForList().size(key);
    }

    /**
     * 读取最近 N 条消息
     */
    public List<Message> getRecentMessages(String conversationId, int n) {
        String key = buildKey(conversationId);

        // LRANGE key -n -1：读取最后 n 条
        List<Object> jsonList = redisTemplate.opsForList().range(key, -n, -1);

        if (CollectionUtils.isEmpty(jsonList)) {
            return new ArrayList<>();
        }

        return jsonList.stream()
            .map(Object::toString)
            .map(this::deserializeMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /**
     * 序列化消息为 JSON
     */
    private String serializeMessage(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("消息序列化失败: {}", message, e);
            return null;
        }
    }

    /**
     * 反序列化 JSON 为消息
     */
    private Message deserializeMessage(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            log.error("消息反序列化失败: {}", json, e);
            return null;
        }
    }
}
