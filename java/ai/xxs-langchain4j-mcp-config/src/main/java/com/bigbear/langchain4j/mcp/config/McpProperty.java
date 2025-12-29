package com.bigbear.langchain4j.mcp.config;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class McpProperty {

    private String command;
    private String transport;
    private List<String> args;
    private Map<String, String> env;
    /**
     * URL（用于 http-sse 传输）
     */
    private String url;

    /**
     * 服务器超时时间（毫秒）
     */
    private Long timeout;
}
