package com.bigbear.ai.biz.common.config;

import com.bigbear.ai.biz.infra.manager.MyChatMemory;
import com.bigbear.ai.biz.service.CustomerService;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringAiMCPConfig {

    //ToolCallbacks.from() 方法会自动查找并注册服务中所有带有 @Tool 注解的方法。
    //todo
    @Bean
    public ToolCallbackProvider crmTools(CustomerService customerService) {
//
//        ToolCallbackProvider from = ToolCallbackProvider.from(ToolCallbacks.from(customerService));
//        return List.of();

      return   MethodToolCallbackProvider.builder().toolObjects(customerService).build();
    }


    /**
     * 使用新的 MessageWindowChatMemory（Redis List 优化版）
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel chatModel,
                                        MessageWindowChatMemory messageWindowChatMemory,
                                        List<McpSyncClient> mcpSyncClients)  {

        // 将 MCP 客户端包装为 ToolCallbackProvider
        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
            .mcpClients(mcpSyncClients)  // 传入所有 MCP 客户端
            .build();

        // 使用新的 MessageWindowChatMemory（基于 Redis List）
        return ChatClient.builder(chatModel)

                .defaultAdvisors(MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build())
                .defaultToolCallbacks(toolCallbackProvider)

                .build();
    }

    /**
     * 保留旧版配置（用于兼容，可选）
     */
    @Bean("legacyOpenAiChatClient")
    public ChatClient legacyOpenAiChatClient(OpenAiChatModel chatModel,
                                              MyChatMemory myChatMemory,
                                              List<McpSyncClient> mcpSyncClients)  {

        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
            .mcpClients(mcpSyncClients)
            .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(myChatMemory).build())
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

}
