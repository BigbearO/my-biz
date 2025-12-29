package com.bigbear.langchain4j.mcp.config;


import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class McpAutoConfig {

    @Bean
    public ToolProvider mcpConfig() {

        McpConfigService mcpConfigService = new McpConfigService();

        return mcpConfigService.getToolProvider();

    }

}
