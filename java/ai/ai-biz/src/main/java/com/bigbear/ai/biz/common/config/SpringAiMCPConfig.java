package com.bigbear.ai.biz.common.config;

import com.bigbear.ai.biz.manager.MyChatMemory;
import com.bigbear.ai.biz.service.CustomerService;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiMCPConfig {

    //ToolCallbacks.from() 方法会自动查找并注册服务中所有带有 @Tool 注解的方法。
    //todo
//    @Bean
//    public List<ToolCallback> crmTools(CustomerService customerService) {
//        return List.of(ToolCallbacks.from(customerService));
//    }


    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel chatModel, MyChatMemory myChatMemory)  {

        ChatClient chatClient =
                ChatClient.create(chatModel);
        // 或使用构建器以获得更多控制
        ChatClient.Builder builder = ChatClient.builder(chatModel).defaultAdvisors(
                MessageChatMemoryAdvisor.builder(myChatMemory).build()
        );
//          chatClient = builder
//            .defaultSystem("你是一个乐于助人的助手。")
//            .build();
        return chatClient;
    }

}
