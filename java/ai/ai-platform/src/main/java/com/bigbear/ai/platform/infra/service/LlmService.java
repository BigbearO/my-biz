package com.bigbear.ai.platform.infra.service;

import com.bigbear.ai.platform.domain.vo.AgentContext;
import com.bigbear.ai.platform.domain.vo.ResponseChunk;
import com.bigbear.ai.platform.infra.manager.ResponseManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM大模型对话服务
 */
@Slf4j
@Service
public class LlmService {

    private final ChatClient chatClient;
    private final TtsService ttsService;

    // 文字分段配置
    private static final int MAX_CHUNK_SIZE = 50;
    private static final int MIN_CHUNK_TRIGGER = 20;

    public LlmService(ChatClient openAiChatClient,
                     TtsService ttsService,
                     ResponseManager responseManager) {
        this.chatClient = openAiChatClient;
        this.ttsService = ttsService;
    }

    /**
     * LLM回调
     */
    public interface LlmCallback {
        void onChunk(ResponseChunk chunk);
        void onComplete();
        void onError(Throwable t);
    }

    /**
     * 对话(异步回调)
     *
     * @param agentContext 智能体上下文
     * @param message 用户消息
     * @param callback 回调
     */
    public void chat(AgentContext agentContext, String message, LlmCallback callback) {
        try {
            AtomicInteger sequence = new AtomicInteger(1);
            StringBuilder fullText = new StringBuilder();

            // 流式调用LLM
            Flux<String> stream = chatClient.prompt()
                .user(message)
                .stream()
                .content();

            stream.subscribe(partialText -> {
                fullText.append(partialText);
                String current = fullText.toString();

                // 检查是否满足切割条件
                if (shouldEmit(current)) {
                    String emitText = extractEmitText(fullText);
                    if (emitText != null && !emitText.isEmpty()) {
                        // 创建文字片段
                        ResponseChunk textChunk = ResponseChunk.builder()
                            .sessionId(agentContext.getSessionId())
                            .type("llm")
                            .sequence(sequence.getAndIncrement())
                            .text(emitText)
                            .isFinal(false)
                            .endsWithPunctuation(isEndsWithPunctuation(emitText))
                            .build();
                        callback.onChunk(textChunk);

                        // 异步调用TTS
                        try {
                            byte[] audioData = ttsService.synthesize(emitText);
                            ResponseChunk audioChunk = ResponseChunk.builder()
                                .sessionId(agentContext.getSessionId())
                                .type("audio")
                                .sequence(textChunk.getSequence())
                                .audioData(audioData)
                                .isFinal(false)
                                .build();
                            callback.onChunk(audioChunk);
                        } catch (Exception e) {
                            log.error("TTS调用失败: {}", e.getMessage());
                        }
                    }
                }
            }, error -> {
                callback.onError(error);
            }, () -> {
                // 处理剩余内容
                if (fullText.length() > 0) {
                    String remaining = fullText.toString().trim();
                    if (!remaining.isEmpty()) {
                        ResponseChunk finalChunk = ResponseChunk.builder()
                            .sessionId(agentContext.getSessionId())
                            .type("llm")
                            .sequence(sequence.get())
                            .text(remaining)
                            .isFinal(true)
                            .endsWithPunctuation(isEndsWithPunctuation(remaining))
                            .build();
                        callback.onChunk(finalChunk);

                        // TTS
                        try {
                            byte[] audioData = ttsService.synthesize(remaining);
                            ResponseChunk audioChunk = ResponseChunk.builder()
                                .sessionId(agentContext.getSessionId())
                                .type("audio")
                                .sequence(finalChunk.getSequence())
                                .audioData(audioData)
                                .isFinal(true)
                                .build();
                            callback.onChunk(audioChunk);
                        } catch (Exception e) {
                            log.error("TTS调用失败: {}", e.getMessage());
                        }
                    }
                }
                callback.onComplete();
            });

        } catch (Exception e) {
            log.error("LLM调用失败", e);
            callback.onError(e);
        }
    }

    /**
     * 判断是否需要发送
     */
    private boolean shouldEmit(String text) {
        if (text == null || text.isEmpty()) return false;
        return text.length() >= MAX_CHUNK_SIZE || isEndsWithPunctuation(text);
    }

    /**
     * 提取需要发送的文字
     */
    private String extractEmitText(StringBuilder accumulator) {
        String text = accumulator.toString();

        // 找到最后一个句末标点
        int lastEndPunct = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if ("。！？!?".indexOf(c) >= 0) {
                lastEndPunct = i + 1;
                break;
            }
        }

        if (lastEndPunct > 0) {
            String result = text.substring(0, lastEndPunct);
            accumulator.delete(0, lastEndPunct);
            return result;
        } else if (text.length() >= MAX_CHUNK_SIZE) {
            String result = text.substring(0, MAX_CHUNK_SIZE);
            accumulator.delete(0, MAX_CHUNK_SIZE);
            return result;
        }

        return null;
    }

    /**
     * 判断是否以句末标点结尾
     */
    private boolean isEndsWithPunctuation(String text) {
        if (text == null || text.isEmpty()) return false;
        char lastChar = text.charAt(text.length() - 1);
        return "。！？!?".indexOf(lastChar) >= 0;
    }
}
