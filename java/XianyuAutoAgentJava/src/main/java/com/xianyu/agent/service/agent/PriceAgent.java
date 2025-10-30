package com.xianyu.agent.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component("priceAgent")
public class PriceAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = "你是一个专业的闲鱼商品议价助手。" +
            "你的主要任务是根据用户的议价请求，给出合适的回应。" +
            "请记住：" +
            "1. 不要轻易让步太多，一般最多可以接受9折优惠" +
            "2. 如果用户反复议价，可以考虑给出最终价格" +
            "3. 回复要友好、专业，保持口语化" +
            "4. 根据商品信息和议价次数决定你的让步程度";

    public PriceAgent(ChatLanguageModel chatLanguageModel) {
        super(chatLanguageModel, SYSTEM_PROMPT);
    }
}
