package com.bigbear.langchain4j.mcp.config;

import java.util.Map;
import lombok.Data;

@Data
public class McpConfig {

    private Map<String, McpProperty> mcpServers;

}
