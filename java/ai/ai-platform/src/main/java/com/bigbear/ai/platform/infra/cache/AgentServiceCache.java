package com.bigbear.ai.platform.infra.cache;

import com.bigbear.ai.platform.domain.vo.AgentContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 智能体服务缓存
 */
@Slf4j
@Component
public class AgentServiceCache {

    private final Cache<String, AgentContext> cache;

    public AgentServiceCache(
            @Value("${voice.chat.cache.agent-service.max-size:1000}") int maxSize,
            @Value("${voice.chat.cache.agent-service.expire-minutes:30}") int expireMinutes) {
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofMinutes(expireMinutes))
                .removalListener((key, value, cause) -> 
                        log.debug("缓存移除: key={}, cause={}", key, cause))
                .build();
        
        log.info("AgentServiceCache初始化: maxSize={}, expireMinutes={}", maxSize, expireMinutes);
    }

    /**
     * 获取缓存
     * 
     * @param agentId 智能体ID
     * @return 智能体上下文
     */
    public AgentContext get(String agentId) {
        return cache.getIfPresent(agentId);
    }

    /**
     * 放入缓存
     * 
     * @param agentId 智能体ID
     * @param context 智能体上下文
     */
    public void put(String agentId, AgentContext context) {
        cache.put(agentId, context);
    }

    /**
     * 移除缓存
     * 
     * @param agentId 智能体ID
     */
    public void invalidate(String agentId) {
        cache.invalidate(agentId);
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * 获取缓存大小
     * 
     * @return 缓存大小
     */
    public long size() {
        return cache.estimatedSize();
    }
}
