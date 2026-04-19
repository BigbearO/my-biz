package com.bigbear.ai.biz.common.config;

import com.bigbear.ai.biz.infra.manager.RedisChatMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置类
 * 配置 MessageWindowChatMemory 和 MessageChatMemoryAdvisor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    private final RedisChatMemoryRepository redisChatMemoryRepository;

    /**
     * 配置 MessageWindowChatMemory
     * 使用 Redis List 作为存储后端，支持滑动窗口
     */
    @Bean
    public MessageWindowChatMemory messageWindowChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * 配置 MessageChatMemoryAdvisor
     * 用于在对话中自动加载上下文
     */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(messageWindowChatMemory()).build();
    }

    /**
     * 可选：创建一个带 MemoryAdvisor 的 ChatClient 构建器
     * 用于简化 ChatClient 的创建
     */
    @Bean
    public ChatClient.Builder chatClientBuilderWithMemory(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor());
    }
}
