package com.bigbear.ai.biz.infra.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiServiceContainer {

    /**
     * 最大缓存条目数（LRU 淘汰触发点）
     */
    private final long maximumSize = 1_00;

    /**
     * 写入后多久过期
     */
    private final Duration ttl = Duration.ofMinutes(60);

    private final Cache<String, Object> cache = CacheBuilder.newBuilder()
        .maximumSize(maximumSize)        // LRU 淘汰
        .expireAfterWrite(ttl)           // TTL
        .recordStats()                   // 可选统计
        .expireAfterAccess(ttl) //每次获取就续期
        .build();

    @Resource
    @Qualifier("myChatModel")
    private  OpenAiChatModel myChatModel;

    @Resource
    @Qualifier("pgChatMemory")
    private ChatMemoryProvider pgChatMemory;


    /* ---------- 对外 API ---------- */
    @SuppressWarnings("unchecked")
    public <V> V get(String key, Class<V> clazz) {
        try {
            return (V) cache.get(key, () -> AiServiceBuilder(clazz));
        } catch (ExecutionException e) {
            throw new RuntimeException("Cache loader failed for key=" + key, e.getCause());
        }
    }


    public void evict(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();

    }


    public   <V> V AiServiceBuilder(Class<V> clazz) {
        return AiServices.builder(clazz)
            .chatModel(myChatModel)
            .chatMemoryProvider(pgChatMemory)
            //todo 构建tokenlistener 参考https://langchain4j.cn/tutorials/observability.html#%E8%81%8A%E5%A4%A9%E6%A8%A1%E5%9E%8B%E5%8F%AF%E8%A7%82%E6%B5%8B%E6%80%A7-chat-model-observability
            .registerListeners()
            .build();
    }
}
