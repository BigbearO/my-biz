package com.bigbear.graph.core.config;


import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public Agent weatherAgent(ChatModel chatModel) {
        return AgentExecutor.builder()
            .chatModel(chatModel)
            .toolsFromObject(new WeatherTool())
            .defaultSystem("你是专业的天气助手，只处理天气相关问题")
            .build()
            .compile();
    }

    @Bean
    public Agent mathAgent(ChatModel chatModel) throws GraphStateException {
        return AgentExecutor.builder()
            .chatModel(chatModel)
            .toolsFromObject(new CalculatorTool())
            .defaultSystem("你是数学专家，擅长解决计算问题")
            .build()
            .compile();
    }


    public static class WeatherTool {
        @Tool(description = "查询指定城市天气")
        public String getWeather(
            @ToolParam(description = "城市名称") String city) {
            // 实际实现调用天气API
            return "北京，晴，25℃";
        }
    }

    public static class CalculatorTool {
        @Tool(description = "执行数学计算")
        public String calculate(
            @ToolParam(description = "数学表达式") String expression) {
            // 实际实现计算逻辑
            return "3 + 5 = 8";
        }
    }
}
