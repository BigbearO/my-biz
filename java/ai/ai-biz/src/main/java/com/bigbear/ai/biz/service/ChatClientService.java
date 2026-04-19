package com.bigbear.ai.biz.service;

import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Spring AI ChatClient 的对话服务
 * 整合了 Redis List 短期存储（滑动窗口）和 PostgreSQL 长期存储（全量历史）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatClientService {

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;

    /**
     * 进行对话
     * 流程：
     * 1. 保存用户消息到 PostgreSQL（长期存储）
     * 2. 调用 ChatClient（自动从 Redis List 读取滑动窗口上下文）
     * 3. 保存 AI 回复到 PostgreSQL（长期存储）
     *
     * @param conversationId 会话 ID
     * @param userMessage    用户消息
     * @return AI 回复内容
     */
    public String chat(String conversationId, String userMessage) {
        log.info("开始对话，conversationId={}", conversationId);

        // 1. 保存用户消息到长期存储（PostgreSQL）
        UserMessage userMsg = new UserMessage(userMessage);
        chatHistoryService.saveMessage(conversationId, userMsg);
        log.debug("用户消息已保存到 PostgreSQL");

        // 2. 调用 ChatClient
        // MessageChatMemoryAdvisor 会自动从 Redis List 读取滑动窗口上下文
        ChatResponse response = chatClient.prompt()
                .user(userMessage)
                .advisors(advisor -> advisor.param("conversation_id", conversationId))
                .call()
                .chatResponse();

        // 3. 获取 AI 回复
        String aiResponse = extractContent(response);
        log.debug("AI 回复：{}", aiResponse);

        // 4. 保存 AI 回复到长期存储（PostgreSQL）
        AssistantMessage assistantMsg = new AssistantMessage(aiResponse);
        chatHistoryService.saveMessage(conversationId, assistantMsg);
        log.debug("AI 回复已保存到 PostgreSQL");

        log.info("对话完成，conversationId={}", conversationId);
        return aiResponse;
    }

    /**
     * 获取会话的完整历史记录（从 PostgreSQL 读取）
     *
     * @param conversationId 会话 ID
     * @return 历史记录列表
     */
    public List<MessageHistoryEntity> getFullHistory(String conversationId) {
        return chatHistoryService.getFullHistory(conversationId);
    }

    /**
     * 从 ChatResponse 中提取文本内容
     */
    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
