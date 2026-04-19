package com.bigbear.ai.biz.service;

import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.biz.infra.manager.MessageHistoryManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * 聊天历史服务
 * 负责将消息保存到 PostgreSQL（长期存储），来一条存一条
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final MessageHistoryManager messageHistoryManager;

    /**
     * 保存单条消息到 PostgreSQL（长期存储）
     * 来一条存一条，INSERT 操作
     *
     * @param conversationId 会话 ID
     * @param message        消息对象
     */
    public void saveMessage(String conversationId, Message message) {
        if (message == null) {
            log.warn("消息为空，跳过保存");
            return;
        }

        try {
            MessageHistoryEntity entity = new MessageHistoryEntity();
            entity.setChatId(conversationId);
            entity.setContent(message.getText());
            entity.setTenantId(1L);
            entity.setRole(detectRole(message));
            entity.setType(detectMessageType(message));
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            messageHistoryManager.save(entity);

            log.debug("保存消息到 PostgreSQL 成功，conversationId={}，role={}",
                    conversationId, entity.getRole());
        } catch (Exception e) {
            log.error("保存消息到 PostgreSQL 失败，conversationId={}", conversationId, e);
            throw new RuntimeException("保存消息失败", e);
        }
    }

    /**
     * 批量保存消息到 PostgreSQL
     *
     * @param conversationId 会话 ID
     * @param messages       消息列表
     */
    public void saveMessages(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            saveMessage(conversationId, message);
        }
    }

    /**
     * 获取会话的完整历史记录（按时间升序）
     *
     * @param conversationId 会话 ID
     * @return 历史记录列表
     */
    public List<MessageHistoryEntity> getFullHistory(String conversationId) {
        List<MessageHistoryEntity> list = messageHistoryManager.lambdaQuery()
                .eq(MessageHistoryEntity::getChatId, conversationId)
                .orderByAsc(MessageHistoryEntity::getCreateTime)
                .list();

        log.debug("查询到历史记录 {} 条，conversationId={}", list.size(), conversationId);
        return list;
    }

    /**
     * 获取用户的完整历史记录
     *
     * @param userId 用户 ID
     * @return 历史记录列表
     */
    public List<MessageHistoryEntity> getHistoryByUserId(String userId) {
        List<MessageHistoryEntity> list = messageHistoryManager.lambdaQuery()
                .eq(MessageHistoryEntity::getUserId, userId)
                .orderByDesc(MessageHistoryEntity::getCreateTime)
                .list();

        log.debug("查询到用户历史记录 {} 条，userId={}", list.size(), userId);
        return list;
    }

    /**
     * 获取商品关联的完整历史记录
     *
     * @param itemId 商品 ID
     * @return 历史记录列表
     */
    public List<MessageHistoryEntity> getHistoryByItemId(String itemId) {
        List<MessageHistoryEntity> list = messageHistoryManager.lambdaQuery()
                .eq(MessageHistoryEntity::getItemId, itemId)
                .orderByDesc(MessageHistoryEntity::getCreateTime)
                .list();

        log.debug("查询到商品历史记录 {} 条，itemId={}", list.size(), itemId);
        return list;
    }

    /**
     * 检测消息角色
     */
    private String detectRole(Message message) {
        if (message instanceof UserMessage) {
            return "user";
        } else if (message instanceof AssistantMessage) {
            return "assistant";
        } else if (message instanceof SystemMessage) {
            return "system";
        }
        return "unknown";
    }

    /**
     * 检测消息类型
     * 0 = System, 1 = User, 2 = Assistant
     */
    private Integer detectMessageType(Message message) {
        if (message instanceof SystemMessage) {
            return 0;
        } else if (message instanceof UserMessage) {
            return 1;
        } else if (message instanceof AssistantMessage) {
            return 2;
        }
        return null;
    }
}
