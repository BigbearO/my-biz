package com.bigbear.aigc.biz.manager;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

@Slf4j
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {
	public static String storeKeyPrefix = "storeKeyPrefix:";

	private final ValueOperations<String, String> valueOperations;

	public static String getKey(Object id) {
		return storeKeyPrefix + id;
	}

	@Override
	public List<ChatMessage> getMessages(Object memoryId) {
		val messages = valueOperations.get(getKey(memoryId));
		return messagesFromJson(messages);
	}

	@Override
	public void updateMessages(Object memoryId, List<ChatMessage> messages) {
		String json = messagesToJson(messages); // 序列化消息
		valueOperations.set(getKey(memoryId), json);
	}

	@Override
	public void deleteMessages(Object memoryId) {
		valueOperations.getOperations().delete(getKey(memoryId));
	}
}
