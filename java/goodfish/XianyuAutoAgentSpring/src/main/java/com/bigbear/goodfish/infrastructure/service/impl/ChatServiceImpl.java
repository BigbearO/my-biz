package com.bigbear.goodfish.infrastructure.service.impl;

import com.bigbear.goodfish.domain.entity.ChatBargainCount;
import com.bigbear.goodfish.domain.entity.Message;
import com.bigbear.goodfish.domain.mapper.ChatBargainCountMapper;
import com.bigbear.goodfish.domain.mapper.MessageMapper;
import com.bigbear.goodfish.domain.service.ChatService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天服务实现
 *
 * @author autoagent
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final ChatBargainCountMapper chatBargainCountMapper;

    public ChatServiceImpl(MessageMapper messageMapper, ChatBargainCountMapper chatBargainCountMapper) {
        this.messageMapper = messageMapper;
        this.chatBargainCountMapper = chatBargainCountMapper;
    }

    @Override
    @Transactional
    public Message saveMessage(String userId, String itemId, String role, String content, String chatId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setItemId(Long.parseLong(itemId));
        message.setRole(role);
        message.setContent(content);
        message.setChatId(chatId);
        message.setTimestamp(LocalDateTime.now());

        messageMapper.insert(message);
        return message;
    }

    @Override
    public List<Message> getChatHistory(String chatId, int limit) {
        if (limit > 0) {
            return messageMapper.findByChatId(chatId, limit);
        } else {
            QueryWrapper<Message> wrapper = new QueryWrapper<>();
            wrapper.eq("chat_id", chatId).orderByDesc("timestamp");
            return messageMapper.selectList(wrapper);
        }
    }

    @Override
    public List<Message> getChatHistoryByUserAndItem(String userId, String itemId, int limit) {
        List<Message> messages = messageMapper.findByUserIdAndItemId(userId, itemId);
        if (limit > 0 && messages.size() > limit) {
            return messages.subList(0, limit);
        }
        return messages;
    }

    @Override
    @Transactional
    public void cleanExpiredMessages(long expireTimeInMillis) {
        LocalDateTime expireTime = LocalDateTime.now().minusSeconds(expireTimeInMillis/1000);
        messageMapper.deleteExpiredMessages(expireTime);
        log.info("已清理过期消息");
    }

    @Override
    public int getBargainCount(String chatId) {
        ChatBargainCount count = chatBargainCountMapper.selectByChatId(chatId);
        return count != null ? count.getCount() : 0;
    }

    @Override
    @Transactional
    public int incrementBargainCount(String chatId) {
        // 使用自定义的incrementCount方法，该方法会处理插入或更新逻辑
        chatBargainCountMapper.incrementCount(chatId);
        // 重新查询获取最新的计数
        ChatBargainCount count = chatBargainCountMapper.selectByChatId(chatId);
        return count != null ? count.getCount() : 1;
    }
}
