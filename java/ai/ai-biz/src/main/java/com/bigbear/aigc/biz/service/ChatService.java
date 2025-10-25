package com.bigbear.aigc.biz.service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

	private final SensitiveWordBs sensitiveWordBs;

	private final RedisTemplate<String, Object> redisTemplate;
	@Resource(name = "myChatModel")
	private ChatModel ChatModel;

	private final PetDoctor petDoctor;

	private String getKey(Serializable userId) {
		return "user:gpt:openai:think:str:" + userId;
	}

	public String chat(Serializable userId, String question) {

		//若上个问题还没有回复完成,直接拒绝回答
		if (Boolean.TRUE.equals(redisTemplate.hasKey(getKey(userId)))) {
			return "抱歉,您刚才那个问题,还在努力思考中!";
		}
		// 检查是否包含敏感词
		boolean contains = sensitiveWordBs.contains(question);
		if (contains) {
			return "包含敏感词汇，换一个问题呢";
		}
		//todo 那个自定义token计算器--CharTokenizer，根据文字字数来算，暂定2个字一个token,

		redisTemplate.opsForValue().set(getKey(userId),question,3, TimeUnit.MINUTES);
		val res =petDoctor.chatPet(1L,question);
		log.info("resp={}", res);

		redisTemplate.delete(getKey(userId));

		return res.replaceAll("(?s)<think>.*?</think>", "");
	}

	// 查找第一个敏感词
//		String firstWord = SensitiveWordHelper.findFirst(question);
//// 查找所有敏感词
//		List<String> allWords = SensitiveWordHelper.findAll(question);
//// 替换敏感词
//		String replaced = SensitiveWordHelper.replace(text);
}
