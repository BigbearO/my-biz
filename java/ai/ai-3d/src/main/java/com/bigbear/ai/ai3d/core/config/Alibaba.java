package com.bigbear.ai.ai3d.core.config;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.openai.client.OpenAIClient;
import java.util.function.BiFunction;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatModel;
//import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

public class Alibaba {

    public static void test() throws GraphRunnerException {

// 初始化 ChatModel
//        OpenAIClient dashScopeApi =
//            OpenAIClient.builder()
//            .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
//            .baseUrl(System.getenv("AI_DASHSCOPE_BASE_URL"))
//            .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
            .baseUrl(SpringUtil.getProperty("llm.chat.url")).build();

        ChatModel chatModel = OpenAiChatModel.builder()
            .options(options)

            .build();

// 定义天气查询工具

        ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
            .description("Get weather for a given city")
            .inputType(String.class)
            .build();

// 创建 agent
        ReactAgent agent = ReactAgent.builder()
            .name("weather_agent")
            .model(chatModel)
            .tools(weatherTool)
            .systemPrompt("You are a helpful assistant")
            .saver(new MemorySaver())
            .build();

// 运行 agent
        AssistantMessage response = agent.call("what is the weather in San Francisco");
        System.out.println(response.getText());
    }


    public static class WeatherTool implements BiFunction<String, ToolContext, String> {

        @Override
        public String apply(String city, ToolContext toolContext) {
            return "It's always sunny in " + city + "!";
        }
    }
}
