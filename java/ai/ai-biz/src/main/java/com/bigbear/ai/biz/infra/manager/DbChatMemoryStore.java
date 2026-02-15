package com.bigbear.ai.biz.infra.manager;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bigbear.ai.biz.common.enums.MessageType;
import com.bigbear.ai.biz.domain.entity.ChatMemoryEntity;
import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 基于PostgreSQL的对话记忆存储，支持tenant_id隔离和滑动窗口算法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class   DbChatMemoryStore implements ChatMemoryStore {

    private static final int MAX_MESSAGES = 10; // 滑动窗口大小

    private final ChatMemoryManager chatMemoryManager;

    private final MessageHistoryManager messageHistoryManager;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            // 从memoryId中提取tenantId
            String memoryIdStr = String.valueOf(memoryId);
            // 从单个ChatMemoryEntity获取消息
            ChatMemoryEntity chatMemory = chatMemoryManager.selectByMemoryId(memoryIdStr);
            if (chatMemory != null && chatMemory.getMessages() != null) {
                return messagesFromJson(chatMemory.getMessages());
            }
        } catch (Exception e) {
            log.error("获取对话记忆失败: {}", e.getMessage(), e);
        }
        return List.of();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {

            if(CollectionUtils.isEmpty(messages)){
                log.info("传入数据为空,memoryId，{}",memoryId);
                return;
            }

            // 从memoryId中提取tenantId、itemId、conversationId
            String memoryIdStr = String.valueOf(memoryId);
            String tenantId = extractTenantId(memoryIdStr);
            String[] parts = memoryIdStr.split("_");
            String itemId = parts.length > 1 ? parts[1] : "";
            String conversationId = parts.length > 2 ? parts[2] : "";

            // 应用滑动窗口算法，只保留最新的MAX_MESSAGES条消息
            List<ChatMessage> windowMessages;
            if (messages.size() > MAX_MESSAGES) {
                windowMessages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
            } else {
                windowMessages = new ArrayList<>(messages);
            }

            String messagesJson = messagesToJson(windowMessages);

            val last = messages.getLast();
            // 检查是否已存在
            ChatMemoryEntity existing = chatMemoryManager.selectByMemoryId(memoryIdStr);

            // 记录最新消息到MessageHistoryEntity
            MessageHistoryEntity messageHistory = new MessageHistoryEntity();
            messageHistory.setTenantId(Long.parseLong(tenantId));
            messageHistory.setChatId(memoryIdStr);
            messageHistory.setItemId(itemId);
            messageHistory.setContent(last.toString());
//            todo 判断是不是来一个就存储一个

            // 设置消息类型
            if (last instanceof dev.langchain4j.data.message.UserMessage) {
                messageHistory.setType(MessageType.USER.getCode());
            } else if (last instanceof dev.langchain4j.data.message.AiMessage) {
                messageHistory.setType(MessageType.AI.getCode());
            } else if (last instanceof dev.langchain4j.data.message.CustomMessage) {
                messageHistory.setType(MessageType.CUSTOM.getCode());
            } else if (last instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
                messageHistory.setType(MessageType.TOOL_EXECUTION_RESULT.getCode());
            } else {
                messageHistory.setType(MessageType.SYSTEM.getCode());
            }
            messageHistory.setUpdateTime(LocalDateTime.now());
            messageHistoryManager.save(messageHistory);


            if (existing != null) {
                // 更新
                LambdaUpdateWrapper<ChatMemoryEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(ChatMemoryEntity::getMemoryId, memoryIdStr)
                    .set(ChatMemoryEntity::getMessages, messagesJson)
                    .set(ChatMemoryEntity::getMessageCount, windowMessages.size())
                    .set(ChatMemoryEntity::getUpdateTime, LocalDateTime.now());
                chatMemoryManager.update(null, updateWrapper);
            } else {
                // 新增
                ChatMemoryEntity entity = new ChatMemoryEntity();
                entity.setMemoryId(memoryIdStr);
                entity.setItemId(itemId);
                entity.setConversationId(conversationId);
                entity.setMessages(messagesJson);
                entity.setMessageCount(windowMessages.size());
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                chatMemoryManager.save(entity);
            }
        } catch (Exception e) {
            log.error("更新对话记忆失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            String memoryIdStr = String.valueOf(memoryId);
            LambdaQueryWrapper<ChatMemoryEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ChatMemoryEntity::getMemoryId, memoryIdStr)
                ;
            chatMemoryManager.remove(queryWrapper);
        } catch (Exception e) {
            log.error("删除对话记忆失败:memoryId={},err： {}", memoryId,e.getMessage(), e);
        }
    }

    /**
     * 从memoryId中提取tenantId memoryId格式: tenantId__itemId__conversationId
     */
    private String extractTenantId(String memoryId) {
        String[] parts = memoryId.split("_");
        return parts.length > 0 ? parts[0] : "default";
    }
}
