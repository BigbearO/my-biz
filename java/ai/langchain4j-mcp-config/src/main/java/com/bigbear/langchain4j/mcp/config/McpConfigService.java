package com.bigbear.langchain4j.mcp.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class McpConfigService {

    private static final String DEFAULT_MCP_URL ="mcp.json";

    /**
     * -- GETTER -- 获取MCP配置
     *
     * @return MCP配置
     */
    private McpConfig mcpConfig;

    private static volatile ToolProvider toolProvider;

    public ToolProvider getToolProvider() {
        if (toolProvider == null) {
            synchronized (McpConfig.class) {
                if (toolProvider == null) {
                    toolProvider = createToolProvider();
                }
            }
        }

        return toolProvider;

    }

    @PostConstruct
    public void init() {
        loadMcpConfig();
    }

    /**
     * 加载MCP配置
     */
    private void loadMcpConfig() {
        try {
            // 改造：不通过 ClassPathResource，使用 getClassLoader().getResourceAsStream() 从 resource 目录获取
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DEFAULT_MCP_URL);
            if (inputStream == null) {
                log.error("mcp.json文件不存在或无法读取");
                return;
            }

            try (InputStream stream = inputStream) {
                String jsonStr = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonObject = JSONUtil.parseObj(jsonStr);

                // 使用hutool的JSON工具进行序列化
                mcpConfig = new McpConfig();
                JSONObject mcpServersJson = jsonObject.getJSONObject("mcpServers");
                if (mcpServersJson != null) {
                    // 手动转换Map
                    Map<String, McpProperty> mcpServersMap = new java.util.HashMap<>();
                    for (Map.Entry<String, Object> entry : mcpServersJson.entrySet()) {
                        McpProperty config = JSONUtil.toBean(JSONUtil.parseObj(entry.getValue()).toString(),
                            McpProperty.class);
                        mcpServersMap.put(entry.getKey(), config);
                    }
                    mcpConfig.setMcpServers(mcpServersMap);
                }

                if (mcpConfig.getMcpServers() == null || mcpConfig.getMcpServers().isEmpty()) {
                    log.warn("mcp.json中没有配置MCP服务器");
                } else {
                    log.info("成功加载MCP配置，包含 {} 个服务器", mcpConfig.getMcpServers().size());

                    // 打印配置信息
                    for (Map.Entry<String, McpProperty> entry : mcpConfig.getMcpServers().entrySet()) {
                        String serverName = entry.getKey();
                        McpProperty config = entry.getValue();
                        log.info("MCP服务器: {}, transport: {}, command: {}, url: {}",
                            serverName, config.getTransport(), config.getCommand(), config.getUrl());
                    }
                }
            }
        } catch (IOException e) {
            log.error("读取mcp.json文件失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("加载MCP配置失败: {}", e.getMessage());
        }
    }





    /**
     * 创建ToolProvider（此方法会在Java版本兼容时实现完整功能）
     *
     * @return ToolProvider实例
     */

    private ToolProvider createToolProvider() {
        if (mcpConfig == null || mcpConfig.getMcpServers() == null || mcpConfig.getMcpServers().isEmpty()) {
            log.warn("MCP配置为空，无法创建ToolProvider");
            return null;
        }

        log.info("开始创建ToolProvider...");

        // 这里由于Java版本兼容性问题，暂时返回配置信息
        // 在实际使用时，可以在这里实现完整的MCP客户端创建逻辑

        java.util.List<McpClient> transportConfigs = new java.util.ArrayList<>();

        for (Map.Entry<String, McpProperty> entry : mcpConfig.getMcpServers().entrySet()) {
            String serverName = entry.getKey();
            McpProperty config = entry.getValue();

            try {
                McpClient transportConfig = createTransportConfig(serverName, config);
                if (transportConfig != null) {
                    transportConfigs.add(transportConfig);
                    log.info("成功创建传输配置: {}", serverName);
                }
            } catch (Exception e) {
                log.error("创建传输配置失败: {} - {}", serverName, e.getMessage());
            }
        }

        if (transportConfigs.isEmpty()) {
            log.warn("没有成功创建任何传输配置");
            return null;
        }

        log.info("ToolProvider创建完成，包含 {} 个传输配置", transportConfigs.size());
        return McpToolProvider.builder()
            .mcpClients(transportConfigs)
            .build();
    }

    /**
     * 创建传输配置（根据transport类型）
     *
     * @param serverName 服务器名称
     * @param config     MCP服务器配置
     * @return 传输配置对象
     */
    private McpClient createTransportConfig(String serverName, McpProperty config) {
        String transportType = config.getTransport();

        if ("sse".equalsIgnoreCase(transportType)) {
            return createHttpTransportConfig(config);
        } else if ("stdio".equalsIgnoreCase(transportType)) {
            return createStdioTransportConfig(config);
        } else {
            log.warn("不支持的传输类型: {}", transportType);
            return null;
        }
    }

    /**
     * 创建HTTP传输配置
     *
     * @param config MCP服务器配置
     * @return HTTP传输配置
     */
    private McpClient createHttpTransportConfig(McpProperty config) {
        if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
            log.error("HTTP传输类型需要配置url字段");
            return null;
        }

        // 根据
        McpTransport transport = new StreamableHttpMcpTransport.Builder()
            .url(config.getUrl())
            // 可以通过env指定一下请求头 ,还可以通过传递一个方法进行请求头获取
            .customHeaders(config.getEnv())

            .logRequests(true) // 如果你想在日志中查看流量
            .logResponses(true)
            .build();

        return new DefaultMcpClient.Builder()

            .transport(transport)

            .build();
    }

    /**
     * 创建STDIO传输配置
     *
     * @param config MCP服务器配置
     * @return STDIO传输配置
     */
    private McpClient createStdioTransportConfig(McpProperty config) {
        if (config.getCommand() == null || config.getCommand().trim().isEmpty()) {
            log.error("STDIO传输类型需要配置command字段");
            return null;
        }

        List<String> command = new java.util.ArrayList<>();
        command.add(config.getCommand());
        command.addAll(config.getArgs());

        McpTransport transport = new StdioMcpTransport.Builder()
            .command(command)

            .environment(config.getEnv())
            .logEvents(true) // 仅当你想在日志中查看流量时
            .build();

        return new DefaultMcpClient.Builder()
            .transport(transport)
            .build();
    }
}

