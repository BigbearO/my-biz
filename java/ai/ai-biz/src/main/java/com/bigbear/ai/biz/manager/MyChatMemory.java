package com.bigbear.ai.biz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MyChatMemory implements ChatMemory {

    private final MessageHistoryManager messageHistoryManager;


    /**
     * 新增对话记忆，不通过此处管理，置空
     */
    @Override
    public void add(String conversationId, List<Message> messages) {

        Set<MessageHistoryEntity> collect = messages.stream().map(message -> {
            MessageHistoryEntity e = new MessageHistoryEntity();
            e.setChatId(conversationId).setTenantId(1L);
            return e;
        }).collect(Collectors.toSet());
        messageHistoryManager.saveBatch(collect);
    }

    @Override
    public List<Message> get(String conversationId) {

        LambdaQueryWrapper<MessageHistoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MessageHistoryEntity::getChatId, conversationId);
        // todo 待优化
        queryWrapper.last("limit 10");

        List<MessageHistoryEntity> list = messageHistoryManager.list(queryWrapper);

        // 对应封装的消息列表
        List<Message> messages = new ArrayList<>();

        for (MessageHistoryEntity messageHistory : list) {

                Integer type = messageHistory.getType();
                switch (type) {
                    case 1 -> messages.add(new UserMessage(messageHistory.getContent()));
                    case 2 -> messages.add(new AssistantMessage(messageHistory.getContent()));
                    case 0 -> messages.add(new SystemMessage(messageHistory.getContent()));
                    default -> throw new IllegalArgumentException("Unknown message type: " + type);
                }
            }


        return messages;
    }

    /**
     * 清除对话记忆，不通过此处管理，置空
     */
    @Override
    public void clear(String conversationId) {

    }

}
