package com.bigbear.ai.platform.infra.manager;

import com.bigbear.ai.platform.domain.vo.ResponseChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 响应管理器
 * 管理文字和语音响应的同步，确保播放顺序正确
 */
@Slf4j
@Component
public class ResponseManager {

    // 每段最大字符数
    private static final int MAX_CHUNK_SIZE = 50;
    
    // 最小触发切割的字符数
    private static final int MIN_CHUNK_TRIGGER = 20;

    /**
     * 分段配置
     */
    public static class ChunkConfig {
        public int maxChunkSize = MAX_CHUNK_SIZE;
        public int minChunkTrigger = MIN_CHUNK_TRIGGER;
    }
    
    private final ChunkConfig config;
    
    // sessionId -> 序号计数器
    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();
    
    // sessionId -> 累积的文字
    private final Map<String, StringBuilder> textAccumulators = new ConcurrentHashMap<>();

    public ResponseManager() {
        this.config = new ChunkConfig();
    }

    public ResponseManager(ChunkConfig config) {
        this.config = config;
    }

    /**
     * 创建响应片段
     * 
     * @param sessionId 会话ID
     * @param text 文字
     * @param audioData 音频数据
     * @return 响应片段
     */
    public ResponseChunk createChunk(String sessionId, String text, byte[] audioData) {
        int sequence = getNextSequence(sessionId);
        boolean isFinal = text == null && audioData == null;
        
        return ResponseChunk.builder()
                .sessionId(sessionId)
                .sequence(sequence)
                .text(text)
                .audioData(audioData)
                .isFinal(isFinal)
                .endsWithPunctuation(isEndsWithPunctuation(text))
                .build();
    }
    
    /**
     * 处理完整响应，进行分段
     * 
     * @param sessionId 会话ID
     * @param fullText 完整文字
     * @return 分段列表
     */
    public List<ResponseChunk> chunkText(String sessionId, String fullText) {
        List<ResponseChunk> chunks = new ArrayList<>();
        
        if (fullText == null || fullText.isEmpty()) {
            return chunks;
        }
        
        StringBuilder buffer = new StringBuilder();
        int sequence = getNextSequence(sessionId);
        
        for (int i = 0; i < fullText.length(); i++) {
            buffer.append(fullText.charAt(i));
            String current = buffer.toString();
            
            // 检查是否满足切割条件
            if (shouldEmit(current)) {
                boolean endsWithPunct = isEndsWithPunctuation(current);
                chunks.add(ResponseChunk.builder()
                        .sessionId(sessionId)
                        .sequence(sequence++)
                        .text(current.trim())
                        .isFinal(false)
                        .endsWithPunctuation(endsWithPunct)
                        .build());
                buffer.setLength(0);
            }
        }
        
        // 处理剩余内容
        if (buffer.length() > 0) {
            chunks.add(ResponseChunk.builder()
                    .sessionId(sessionId)
                    .sequence(sequence)
                    .text(buffer.toString().trim())
                    .isFinal(true)
                    .endsWithPunctuation(isEndsWithPunctuation(buffer.toString()))
                    .build());
        }
        
        // 标记最后一段
        if (!chunks.isEmpty()) {
            chunks.get(chunks.size() - 1).setFinal(true);
        }
        
        return chunks;
    }
    
    /**
     * 获取下一个序号
     */
    private int getNextSequence(String sessionId) {
        return sequenceCounters.computeIfAbsent(sessionId, k -> new AtomicInteger(1))
                .getAndIncrement();
    }
    
    /**
     * 判断是否需要发送
     */
    private boolean shouldEmit(String text) {
        if (text == null || text.isEmpty()) return false;
        return text.length() >= config.maxChunkSize || isEndsWithPunctuation(text);
    }
    
    /**
     * 判断是否以句末标点结尾
     */
    private boolean isEndsWithPunctuation(String text) {
        if (text == null || text.isEmpty()) return false;
        char lastChar = text.charAt(text.length() - 1);
        return "。！？!?".indexOf(lastChar) >= 0;
    }
    
    /**
     * 重置会话
     * 
     * @param sessionId 会话ID
     */
    public void reset(String sessionId) {
        sequenceCounters.remove(sessionId);
        textAccumulators.remove(sessionId);
    }
    
    /**
     * 清理所有会话
     */
    public void clear() {
        sequenceCounters.clear();
        textAccumulators.clear();
    }
}
