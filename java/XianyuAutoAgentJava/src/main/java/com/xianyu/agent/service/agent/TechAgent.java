package com.xianyu.agent.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component("techAgent")
public class TechAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = "你是一个专业的技术顾问。" +
            "你的任务是回答用户关于商品的技术问题。" +
            "请记住：" +
            "1. 回答要专业、准确" +
            "2. 使用通俗易懂的语言解释技术细节" +
            "3. 如果不确定，不要随意猜测，可以建议用户咨询更专业的人士" +
            "4. 保持友好的沟通态度";

    public TechAgent(ChatLanguageModel chatLanguageModel) {
        super(chatLanguageModel, SYSTEM_PROMPT);
    }
}
