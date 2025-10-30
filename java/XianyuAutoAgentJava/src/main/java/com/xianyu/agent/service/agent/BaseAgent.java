package com.xianyu.agent.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class BaseAgent implements Agent {

    protected final ChatLanguageModel chatLanguageModel;
    protected final String systemPrompt;

    @Value("${llm.max-tokens:2048}")
    protected int maxTokens;

    @Value("${llm.temperature:0.7}")
    protected double temperature;

    public BaseAgent(ChatLanguageModel chatLanguageModel, String systemPrompt) {
        this.chatLanguageModel = chatLanguageModel;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String generate(String userMessage, List<Map<String, String>> context, Map<String, Object> itemInfo) {
        List<dev.langchain4j.data.message.ChatMessage> messages = _buildMessages(userMessage, context, itemInfo);
        return _callLLM(messages);
    }

    protected List<dev.langchain4j.data.message.ChatMessage> _buildMessages(String userMessage, List<Map<String, String>> context, Map<String, Object> itemInfo) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        
        // 添加系统提示
        messages.add(new SystemMessage(systemPrompt));
        
        // 添加商品信息到上下文
        if (itemInfo != null && !itemInfo.isEmpty()) {
            String itemInfoStr = "商品信息：" + itemInfo.toString();
            messages.add(new SystemMessage(itemInfoStr));
        }
        
        // 添加历史对话
        for (Map<String, String> msg : context) {
            if ("user".equals(msg.get("role"))) {
                messages.add(new UserMessage(msg.get("content")));
            } else if ("assistant".equals(msg.get("role"))) {
                messages.add(new AiMessage(msg.get("content")));
            }
        }
        
        // 添加当前用户消息
        messages.add(new UserMessage(userMessage));
        
        return messages;
    }

    protected String _callLLM(List<dev.langchain4j.data.message.ChatMessage> messages) {
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }
}
