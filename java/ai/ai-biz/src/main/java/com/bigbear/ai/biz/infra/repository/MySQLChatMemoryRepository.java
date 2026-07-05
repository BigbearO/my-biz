package com.bigbear.ai.biz.infra.repository;

import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.biz.infra.mapper.MessageHistoryMapper;
import com.mybatisflex.core.query.QueryWrapper;
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

@Slf4j
@Repository
@RequiredArgsConstructor
public class MySQLChatMemoryRepository {

    private final MessageHistoryMapper messageHistoryMapper;

    public void saveMessage(String conversationId, Message message) {
        if (message == null) {
            return;
        }
        MessageHistoryEntity entity = new MessageHistoryEntity();
        entity.setChatId(conversationId);
        entity.setContent(message.getText());
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
        QueryWrapper wrapper = QueryWrapper.create()
            .from(MessageHistoryEntity.class)
            .where(MessageHistoryEntity::getChatId).eq(conversationId)
            .orderBy(MessageHistoryEntity::getCreateTime, true)
            .limit(limit);
        try {
            return messageHistoryMapper.selectListByQuery(wrapper)
                    .stream()
                    .map(this::toMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("MySQL findRecentMessages 失败，conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    public List<MessageHistoryEntity> findAllByConversationId(String conversationId) {
        QueryWrapper wrapper = QueryWrapper.create()
            .from(MessageHistoryEntity.class)
            .where(MessageHistoryEntity::getChatId).eq(conversationId)
            .orderBy(MessageHistoryEntity::getCreateTime, true);
        return messageHistoryMapper.selectListByQuery(wrapper);
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
                log.warn("Unknown message type: {}, skipping", entity.getType());
                yield null;
            }
        };
    }
}
