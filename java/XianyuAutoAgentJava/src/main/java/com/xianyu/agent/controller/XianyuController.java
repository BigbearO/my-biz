package com.xianyu.agent.controller;

import com.xianyu.agent.service.XianyuWebSocketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class XianyuController {

    @Autowired
    private XianyuWebSocketClient webSocketClient;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "healthy");
        result.put("service", "XianyuAutoAgentJava");
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        // 这里可以返回WebSocket连接状态、当前活跃聊天数等信息
        result.put("status", "running");
        return result;
    }

    @GetMapping("/reconnect")
    public Map<String, Object> reconnect() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 触发重新连接
            result.put("status", "success");
            result.put("message", "重新连接已触发");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
