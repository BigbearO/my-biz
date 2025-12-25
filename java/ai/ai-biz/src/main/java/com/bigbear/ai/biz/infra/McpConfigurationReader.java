package com.bigbear.ai.biz.infra;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP配置读取器
 * 从resources/mcp.json中读取配置并转换为langchain4j框架的MCP工具列表
 */
@Component
@Slf4j
public class McpConfigurationReader {
    
//    private static final Logger logger = LoggerFactory.getLogger(McpConfigurationReader.class);
    private static final String MCP_CONFIG_FILE = "mcp.json";
    
    /**
     * MCP服务器配置类
     */
    public static class McpServerConfig {
        private String command;
        private String transport;
        private List<String> args;
        private Map<String, String> env;
        
        public String getCommand() {
            return command;
        }
        
        public void setCommand(String command) {
            this.command = command;
        }
        
        public String getTransport() {
            return transport;
        }
        
        public void setTransport(String transport) {
            this.transport = transport;
        }
        
        public List<String> getArgs() {
            return args;
        }
        
        public void setArgs(List<String> args) {
            this.args = args;
        }
        
        public Map<String, String> getEnv() {
            return env;
        }
        
        public void setEnv(Map<String, String> env) {
            this.env = env;
        }
        
        @Override
        public String toString() {
            return "McpServerConfig{" +
                    "command='" + command + '\'' +
                    ", transport='" + transport + '\'' +
                    ", args=" + args +
                    ", env=" + env +
                    '}';
        }
    }
    
    /**
     * langchain4j MCP工具配置类
     */
    public static class Langchain4jMcpTool {
        private String name;
        private String description;
        private String transport;
        private String command;
        private List<String> args;
        private Map<String, String> env;
        
        public Langchain4jMcpTool() {}
        
        public Langchain4jMcpTool(String name, McpServerConfig config) {
            this.name = name;
            this.transport = config.getTransport();
            this.command = config.getCommand();
            this.args = config.getArgs();
            this.env = config.getEnv();
            this.description = "MCP tool: " + name;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getTransport() {
            return transport;
        }
        
        public void setTransport(String transport) {
            this.transport = transport;
        }
        
        public String getCommand() {
            return command;
        }
        
        public void setCommand(String command) {
            this.command = command;
        }
        
        public List<String> getArgs() {
            return args;
        }
        
        public void setArgs(List<String> args) {
            this.args = args;
        }
        
        public Map<String, String> getEnv() {
            return env;
        }
        
        public void setEnv(Map<String, String> env) {
            this.env = env;
        }
        
        @Override
        public String toString() {
            return "Langchain4jMcpTool{" +
                    "name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", transport='" + transport + '\'' +
                    ", command='" + command + '\'' +
                    ", args=" + args +
                    ", env=" + env +
                    '}';
        }
    }
    
    /**
     * 读取MCP配置文件
     * @return MCP服务器配置映射
     */
    public Map<String, McpServerConfig> readMcpConfiguration() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MCP_CONFIG_FILE)) {
            if (is == null) {
                log.error("MCP配置文件未找到: {}", MCP_CONFIG_FILE);
                return new HashMap<>();
            }
            
            String jsonContent = new String(is.readAllBytes());
            JSONObject jsonObject = JSON.parseObject(jsonContent);
            JSONObject mcpServers = jsonObject.getJSONObject("mcpServers");
            
            if (mcpServers == null) {
                log.warn("MCP配置文件中未找到mcpServers节点");
                return new HashMap<>();
            }
            
            Map<String, McpServerConfig> configMap = new HashMap<>();
            for (String key : mcpServers.keySet()) {
                JSONObject serverConfig = mcpServers.getJSONObject(key);
                McpServerConfig config = serverConfig.toJavaObject(McpServerConfig.class);
                configMap.put(key, config);
            }
            
            log.info("成功读取MCP配置，共找到{}个服务器配置", configMap.size());
            return configMap;
            
        } catch (IOException e) {
            log.error("读取MCP配置文件失败", e);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("解析MCP配置文件失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 将MCP配置转换为langchain4j工具列表
     * @return langchain4j MCP工具列表
     */
    public List<Langchain4jMcpTool> convertToLangchain4jTools() {
        Map<String, McpServerConfig> mcpConfigs = readMcpConfiguration();
        List<Langchain4jMcpTool> tools = new ArrayList<>();
        
        for (Map.Entry<String, McpServerConfig> entry : mcpConfigs.entrySet()) {
            String serverName = entry.getKey();
            McpServerConfig config = entry.getValue();
            
            Langchain4jMcpTool tool = new Langchain4jMcpTool(serverName, config);
            tools.add(tool);
            
            log.debug("转换MCP工具: {} -> {}", serverName, tool);
        }
        
        log.info("成功转换{}个MCP配置为langchain4j工具", tools.size());
        return tools;
    }
    
    /**
     * 获取指定名称的MCP工具配置
     * @param toolName 工具名称
     * @return MCP工具配置，未找到返回null
     */
    public Langchain4jMcpTool getMcpTool(String toolName) {
        Map<String, McpServerConfig> mcpConfigs = readMcpConfiguration();
        McpServerConfig config = mcpConfigs.get(toolName);
        
        if (config != null) {
            return new Langchain4jMcpTool(toolName, config);
        }
        
        log.warn("未找到MCP工具: {}", toolName);
        return null;
    }
    
    /**
     * 获取所有MCP工具名称
     * @return 工具名称列表
     */
    public List<String> getAllMcpToolNames() {
        Map<String, McpServerConfig> mcpConfigs = readMcpConfiguration();
        return new ArrayList<>(mcpConfigs.keySet());
    }
}