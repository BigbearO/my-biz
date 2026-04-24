package com.bigbear.ai.biz.common.config;

import com.bigbear.ai.biz.service.HybridChatMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    private final HybridChatMemoryService hybridChatMemoryService;

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(hybridChatMemoryService)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(messageWindowChatMemory()).build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilderWithMemory(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(messageChatMemoryAdvisor());
    }
}
