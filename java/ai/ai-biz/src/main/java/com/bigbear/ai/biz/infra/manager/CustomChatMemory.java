package com.bigbear.ai.biz.infra.manager;

import com.bigbear.ai.biz.infra.repository.MySQLChatMemoryStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomChatMemory implements ChatMemory {

    private final RedisChatMemoryStore redisStore;
    private final MySQLChatMemoryStore mysqlStore;

    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;

    @Override
    public List<Message> get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = redisStore.getMessages(conversationId);
        if (messages.isEmpty()) {
            log.debug("Redis 空，fallback 到 MySQL，conversationId={}", conversationId);
            messages = mysqlStore.findRecentMessages(conversationId, maxMessages);
            if (!messages.isEmpty()) {
                log.debug("MySQL 查询到 {} 条消息，回填 Redis，conversationId={}", messages.size(), conversationId);
                redisStore.saveMessages(conversationId, messages);
            }
        }
        return messages;
    }

    @Override
    public void add(String conversationId, @NonNull List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        List<Message> memoryMessages = redisStore.getMessages(conversationId);
        List<Message> processedMessages = this.process(memoryMessages, messages);

        for (Message message : messages) {
            mysqlStore.saveMessage(conversationId, message);
        }

        redisStore.saveMessages(conversationId, processedMessages);
    }

    @Override
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        redisStore.deleteMessages(conversationId);
    }

    private List<Message> process(List<Message> memoryMessages, List<Message> newMessages) {
        List<Message> processedMessages = new ArrayList<>();
        Set<Message> memoryMessagesSet = new HashSet<>(memoryMessages);

        Stream<Message> stream = newMessages.stream();
        Objects.requireNonNull(SystemMessage.class);
        boolean hasNewSystemMessage = stream.filter(SystemMessage.class::isInstance)
                .anyMatch((messagex) -> !memoryMessagesSet.contains(messagex));

        stream = memoryMessages.stream()
                .filter((messagex) -> !hasNewSystemMessage || !(messagex instanceof SystemMessage));
        Objects.requireNonNull(processedMessages);
        stream.forEach(processedMessages::add);

        processedMessages.addAll(newMessages);

        if (processedMessages.size() <= this.maxMessages) {
            return processedMessages;
        } else {
            int messagesToRemove = processedMessages.size() - this.maxMessages;
            List<Message> trimmedMessages = new ArrayList<>();
            int removed = 0;

            for (Message message : processedMessages) {
                if (!(message instanceof SystemMessage) && removed < messagesToRemove) {
                    ++removed;
                } else {
                    trimmedMessages.add(message);
                }
            }

            return trimmedMessages;
        }
    }
}
