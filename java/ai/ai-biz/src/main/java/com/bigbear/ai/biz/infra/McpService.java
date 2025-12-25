package com.bigbear.ai.biz.infra;

import com.bigbear.ai.biz.infra.McpConfigurationReader.Langchain4jMcpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP服务类
 * 提供与langchain4j框架集成的MCP工具服务
 */
@Service
@Slf4j
public class McpService {
    
 
    
    @Autowired
    private McpConfigurationReader mcpConfigurationReader;
    
    /**
     * 获取所有可用的MCP工具
     * @return MCP工具列表
     */
    public List<Langchain4jMcpTool> getAllMcpTools() {
        return mcpConfigurationReader.convertToLangchain4jTools();
    }
    
    /**
     * 根据工具名称获取MCP工具
     * @param toolName 工具名称
     * @return MCP工具配置，未找到返回null
     */
    public Langchain4jMcpTool getMcpTool(String toolName) {
        return mcpConfigurationReader.getMcpTool(toolName);
    }
    
    /**
     * 获取所有MCP工具名称
     * @return 工具名称列表
     */
    public List<String> getAllMcpToolNames() {
        return mcpConfigurationReader.getAllMcpToolNames();
    }
    
    /**
     * 检查指定的MCP工具是否存在
     * @param toolName 工具名称
     * @return 是否存在
     */
    public boolean hasMcpTool(String toolName) {
        return mcpConfigurationReader.getMcpTool(toolName) != null;
    }
    
    /**
     * 根据传输类型筛选MCP工具
     * @param transport 传输类型 (如: stdio, http, websocket)
     * @return 符合条件的MCP工具列表
     */
    public List<Langchain4jMcpTool> getMcpToolsByTransport(String transport) {
        List<Langchain4jMcpTool> allTools = getAllMcpTools();
        return allTools.stream()
                .filter(tool -> transport.equals(tool.getTransport()))
                .collect(Collectors.toList());
    }
    
    /**
     * 创建MCP工具执行命令
     * @param tool MCP工具配置
     * @return 完整的执行命令列表
     */
    public List<String> createExecutionCommand(Langchain4jMcpTool tool) {
        List<String> command = new java.util.ArrayList<>();
        command.add(tool.getCommand());
        
        if (tool.getArgs() != null && !tool.getArgs().isEmpty()) {
            command.addAll(tool.getArgs());
        }
        
        return command;
    }
    
    /**
     * 设置MCP工具的环境变量
     * @param tool MCP工具配置
     */
    public void setupEnvironment(Langchain4jMcpTool tool) {
        Map<String, String> env = tool.getEnv();
        if (env != null && !env.isEmpty()) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                if (value != null && !value.trim().isEmpty()) {
                    System.setProperty(key, value);
                    log.debug("设置环境变量: {} = {}", key, value);
                } else {
                    log.warn("环境变量 {} 的值为空，跳过设置", key);
                }
            }
        }
    }
    
    /**
     * 验证MCP工具配置的完整性
     * @param tool MCP工具配置
     * @return 验证结果
     */
    public boolean validateMcpTool(Langchain4jMcpTool tool) {
        if (tool == null) {
            log.error("MCP工具配置为空");
            return false;
        }
        
        if (tool.getName() == null || tool.getName().trim().isEmpty()) {
            log.error("MCP工具名称为空");
            return false;
        }
        
        if (tool.getCommand() == null || tool.getCommand().trim().isEmpty()) {
            log.error("MCP工具命令为空");
            return false;
        }
        
        if (tool.getTransport() == null || tool.getTransport().trim().isEmpty()) {
            log.error("MCP工具传输类型为空");
            return false;
        }
        
        log.debug("MCP工具配置验证通过: {}", tool.getName());
        return true;
    }
    
    /**
     * 获取MCP工具统计信息
     * @return 统计信息映射
     */
    public Map<String, Object> getMcpToolStatistics() {
        List<Langchain4jMcpTool> allTools = getAllMcpTools();
        
        Map<String, Long> transportCount = allTools.stream()
                .collect(Collectors.groupingBy(
                        Langchain4jMcpTool::getTransport,
                        Collectors.counting()
                ));
        
        Map<String, Object> statistics = new java.util.HashMap<>();
        statistics.put("totalTools", allTools.size());
        statistics.put("transportTypes", transportCount);
        statistics.put("toolNames", allTools.stream()
                .map(Langchain4jMcpTool::getName)
                .collect(Collectors.toList()));
        
        return statistics;
    }
}