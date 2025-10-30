package com.xianyu.agent.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component("defaultAgent")
public class DefaultAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = "你是一个友好的闲鱼客服助手。" +
            "你的任务是回答用户的各种问题，提供良好的购物体验。" +
            "请记住：" +
            "1. 保持友好、热情的态度" +
            "2. 回答要简洁明了" +
            "3. 如果用户询问商品信息，请根据提供的商品信息进行回答" +
            "4. 鼓励用户下单购买";

    public DefaultAgent(ChatLanguageModel chatLanguageModel) {
        super(chatLanguageModel, SYSTEM_PROMPT);
    }
}
