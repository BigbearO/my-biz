package com.bigbear.ai.ai3d.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.ai.ai3d.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.ai3d.infra.mapper.MessageHistoryMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MySQLChatMemoryStore {

    private final MessageHistoryMapper messageHistoryMapper;

    @Transactional
    public void saveMessage(String conversationId, Message message) {
        if (message == null) {
            return;
        }
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getText());
        entity.setTenantId(1L);
        entity.setType(detectMessageType(message));
        entity.setRole(detectRole(message));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        try {
            messageHistoryMapper.insert(entity);
            log.debug("MySQL 保存消息成功，conversationId={}，role={}", conversationId, entity.getRole());
        } catch (Exception e) {
            log.error("MySQL saveMessage 失败，conversationId={}", conversationId, e);
            throw e;
        }
    }

    public List<Message> findRecentMessages(String conversationId, int limit) {
        LambdaQueryWrapper<MessageHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageHistoryEntity::getChatId, conversationId)
                .orderByAsc(MessageHistoryEntity::getCreateTime)
                .last("LIMIT " + limit);
        try {
            return messageHistoryMapper.selectList(wrapper)
                    .stream()
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("MySQL findRecentMessages 失败，conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    private int detectMessageType(Message message) {
        return switch (message.getMessageType()) {
            case USER -> 1;
            case ASSISTANT -> 2;
            case SYSTEM -> 0;
            default -> 3;
        };
    }

    private String detectRole(Message message) {
        return message.getMessageType().name().toLowerCase();
    }

    private Message toMessage(MessageHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return switch (entity.getType()) {
            case 1 -> new UserMessage(entity.getContent());
            case 2 -> new AssistantMessage(entity.getContent());
            case 0 -> new SystemMessage(entity.getContent());
            default -> {
                log.warn("Unknown type: {}, skipping", entity.getType());
                yield null;
            }
        };
    }
}
