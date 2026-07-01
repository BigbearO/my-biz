package com.bigbear.ai.ai3d.core.config;

import com.bigbear.ai.ai3d.infra.memory.CustomChatMemory;
import com.bigbear.ai.ai3d.infra.service.CustomerService;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
//import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StreamUtils;

@Configuration
@Slf4j
public class SpringAiConfig {

    @Bean
    public ToolCallbackProvider crmTools(CustomerService customerService) {
        return MethodToolCallbackProvider.builder().toolObjects(customerService).build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(CustomChatMemory customChatMemory) {
        return MessageChatMemoryAdvisor.builder(customChatMemory).build();
    }

    @Bean
    @Primary
    public ChatClient openAiChatClient(OpenAiChatModel chatModel, CustomChatMemory chatMemory,
        List<McpSyncClient> mcpSyncClients) {

        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
            .mcpClients(mcpSyncClients)
            .build();
        
        Builder builder = ChatClient.builder(chatModel);
        try {
            String s = readSystemPromptValue();
            builder. defaultSystem(s);
        }catch (IOException e){
            log.error(e.getMessage(),e);
        }

        return builder
            .defaultAdvisors(new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(chatMemory).build())
//            .defaultToolCallbacks(toolCallbackProvider)
            .defaultTools(toolCallbackProvider)
            .build();
    }

//    @Bean
//    public McpSyncClientCustomizer toolsChangeListener() {
//        return (name, clientSpec) -> clientSpec.toolsChangeConsumer(changeEvent -> {
//            log.info("MCP 工具发生变化: {}", changeEvent);
//        });
//    }

    public String readSystemPromptValue() throws IOException {
        ClassLoader classLoader = this.getClass().getClassLoader();
        String path = "prompts/system-prompt.txt";
        return StreamUtils.copyToString(classLoader.getResourceAsStream(path), StandardCharsets.UTF_8);
    }
}
