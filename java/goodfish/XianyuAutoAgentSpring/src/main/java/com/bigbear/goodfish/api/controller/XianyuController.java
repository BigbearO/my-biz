package com.bigbear.goodfish.api.controller;

import com.bigbear.goodfish.domain.service.WebSocketService;
import com.bigbear.goodfish.infrastructure.client.GoodFishApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 闲鱼控制器
 *
 * @author autoagent
 */
@Slf4j
@RestController
@RequestMapping("/api/xianyu")
public class XianyuController {

    private final GoodFishApiClient goodFishApiClient;
    private final WebSocketService webSocketService;

    public XianyuController(GoodFishApiClient goodFishApiClient, WebSocketService webSocketService) {
        this.goodFishApiClient = goodFishApiClient;
        this.webSocketService = webSocketService;
    }

    /**
     * 初始化连接
     */
    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestBody Map<String, String> request) {
        String cookiesStr = request.get("cookies");
        if (cookiesStr == null || cookiesStr.isEmpty()) {
            return Map.of("success", false, "message", "缺少cookies参数");
        }

        try {
            webSocketService.initConnection(cookiesStr);
            return Map.of("success", true, "message", "连接成功");
        } catch (Exception e) {
            log.error("连接失败", e);
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }

    /**
     * 断开连接
     */
    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        try {
            webSocketService.closeConnection();
            return Map.of("success", true, "message", "断开连接成功");
        } catch (Exception e) {
            log.error("断开连接失败", e);
            return Map.of("success", false, "message", "断开连接失败: " + e.getMessage());
        }
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh-token")
    public Map<String, Object> refreshToken() {
        try {
            String token = webSocketService.refreshToken();
            if (token != null) {
                return Map.of("success", true, "message", "Token刷新成功", "token", token);
            } else {
                return Map.of("success", false, "message", "Token刷新失败");
            }
        } catch (Exception e) {
            log.error("Token刷新失败", e);
            return Map.of("success", false, "message", "Token刷新失败: " + e.getMessage());
        }
    }

    /**
     * 切换人工模式
     */
    @PostMapping("/manual-mode")
    public Map<String, Object> toggleManualMode(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        if (conversationId == null || conversationId.isEmpty()) {
            return Map.of("success", false, "message", "缺少conversationId参数");
        }

        try {
            webSocketService.toggleManualMode(conversationId);
            boolean isManualMode = webSocketService.isManualMode(conversationId);
            return Map.of(
                    "success", true,
                    "message", "切换成功",
                    "manualMode", isManualMode
            );
        } catch (Exception e) {
            log.error("切换人工模式失败", e);
            return Map.of("success", false, "message", "切换人工模式失败: " + e.getMessage());
        }
    }
}
