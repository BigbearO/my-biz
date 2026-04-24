package com.bigbear.ai.biz.service;

import com.bigbear.ai.biz.infra.manager.RedisChatMemoryRepository;
import com.bigbear.ai.biz.infra.repository.MySQLChatMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridChatMemoryService implements ChatMemoryRepository {

    private final RedisChatMemoryRepository redisRepo;
    private final MySQLChatMemoryRepository mysqlRepo;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    @Override
    public List<String> findConversationIds() {
        //   可以获取当前用户id,再去查询
//        Long userId = LoginHelper.getUserId();
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = redisRepo.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            log.debug("Redis 空， fallback 到 MySQL， conversationId={}", conversationId);
            messages = mysqlRepo.findRecentMessages(conversationId, maxMessages);
            if (!messages.isEmpty()) {
                log.debug("MySQL 查询到 {} 条消息，填充到 Redis， conversationId={}", messages.size(), conversationId);
                redisRepo.saveAll(conversationId, messages);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (conversationId.isBlank()) {
            return;
        }
        if (messages.isEmpty()) {
            return;
        }
        for (Message message : messages) {
            mysqlRepo.saveMessage(conversationId, message);
        }
        redisRepo.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId.isBlank()) {
            return;
        }
        redisRepo.deleteByConversationId(conversationId);
    }

}
