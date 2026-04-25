package com.bigbear.ai.biz.common.config;

import com.bigbear.ai.biz.infra.manager.CustomChatMemory;
import com.bigbear.ai.biz.infra.manager.MyChatMemory;
import com.bigbear.ai.biz.service.CustomerService;
import io.modelcontextprotocol.client.McpSyncClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
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

    //ToolCallbacks.from() 方法会自动查找并注册服务中所有带有 @Tool 注解的方法。
    //todo
    @Bean
    public ToolCallbackProvider crmTools(CustomerService customerService) {
//
//        ToolCallbackProvider from = ToolCallbackProvider.from(ToolCallbacks.from(customerService));
//        return List.of();

        return MethodToolCallbackProvider.builder().toolObjects(customerService).build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(CustomChatMemory customChatMemory) {
        return MessageChatMemoryAdvisor.builder(customChatMemory).build();
    }

    /**
     * 使用新的 MessageWindowChatMemory（Redis List 优化版）
     */
    @Bean
    @Primary
    public ChatClient openAiChatClient(OpenAiChatModel chatModel, CustomChatMemory chatMemory,
        List<McpSyncClient> mcpSyncClients) {

        // 将 MCP 客户端包装为 ToolCallbackProvider
        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
            .mcpClients(mcpSyncClients)  // 传入所有 MCP 客户端
            .build();

        // 使用新的 MessageWindowChatMemory（基于 Redis List）
        return ChatClient.builder(chatModel)

            .defaultAdvisors (new SimpleLoggerAdvisor(), MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultToolCallbacks(toolCallbackProvider)

            .build();
    }


    /**
     * 可选：监听 MCP 工具变更[citation:2][citation:9]
     * 当服务器动态添加/移除工具时，客户端可以收到通知
     */
    @Bean
    public McpSyncClientCustomizer toolsChangeListener() {
        return (name, clientSpec) -> clientSpec.toolsChangeConsumer(changeEvent -> {
            log.info("MCP 工具发生变化: {}", changeEvent);
            // 这里可以更新客户端状态或重新加载工具
        });
    }

    public String readSystemPromptValue() throws IOException {
        ClassLoader classLoader = this.getClass().getClassLoader();
        String path = "prompts/system-prompt.txt";
        return StreamUtils.copyToString(classLoader.getResourceAsStream(path), StandardCharsets.UTF_8);
    }
    /**
     * 保留旧版配置（用于兼容，可选）
     */
//    @Bean("legacyOpenAiChatClient")
    public ChatClient legacyOpenAiChatClient(OpenAiChatModel chatModel,
        MyChatMemory myChatMemory,
        List<McpSyncClient> mcpSyncClients) {

        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
            .mcpClients(mcpSyncClients)
            .build();

        return ChatClient.builder(chatModel)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(myChatMemory).build())
            .defaultToolCallbacks(toolCallbackProvider)
            .build();
    }

}
