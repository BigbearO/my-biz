package com.xianyu.agent.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component("classifyAgent")
public class ClassifyAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = "你是一个消息分类器。" +
            "请根据用户消息的内容，将其分类为以下类别之一：" +
            "1. price - 价格相关（包含议价、优惠等内容）" +
            "2. tech - 技术相关（包含产品参数、功能等询问）" +
            "3. order - 订单相关（包含下单、购买、付款等内容）" +
            "4. other - 其他内容" +
            "请只返回类别名称，不要返回其他任何内容。";

    public ClassifyAgent(ChatLanguageModel chatLanguageModel) {
        super(chatLanguageModel, SYSTEM_PROMPT);
    }
}
