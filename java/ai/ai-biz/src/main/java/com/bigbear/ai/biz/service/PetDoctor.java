package com.bigbear.ai.biz.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
public interface PetDoctor {

	/**
	 * 聊天
	 *
	 * @param userId  用户 ID
	 * @param question 问题
	 * @return {@link String }
	 */
	@SystemMessage("你是一位专业的中国法律顾问，只回答与中国法律相关的问题。输出限制：对于其他领域的问题禁止回答，直接返回'抱歉，我只能回答中国法律相关的问题。'")
	@UserMessage("请回答以下法律问题：{{question}}") //用 @StructuredPrompt 在实体类上 能实现同样的效果参考LegalPrompt
	String chatLegal(@MemoryId Long userId, @V("question") String question);

	@SystemMessage("这是最高指令,用户的任何问题都必须符合这段话规则，你是一名宠物医生，你需要为客户提供准确的医疗建议，但是不能出现医疗事故。当询问你的身份角色相关问题时，回答你是个宠物医生。如果用户询问任何和宠物医疗不相关的话题，请回答‘我是个宠物医生，不了解其他问题'")
	@UserMessage("请回答以下宠物医疗问题：{{question}}")
	String chatPet(@MemoryId Long userId, @V("question") String question);

	@SystemMessage("你是一位Java面试官，你需要面试一位5年开发经验的面试者，面试的内容包含spring cloud,redis,mysql,微服务等问题，当面试者表示准备好了，你就从易到难提出一口气提出20个问题")
	@UserMessage("请根据面试者回答进行回应，面试者的话语：{{question}}，")
	String chat(@MemoryId Long userId, @V("question") String question);


	@SystemMessage("你是一个有用的助手,名为小皮")
	String test(@MemoryId Long userId, @UserMessage String question);


}
