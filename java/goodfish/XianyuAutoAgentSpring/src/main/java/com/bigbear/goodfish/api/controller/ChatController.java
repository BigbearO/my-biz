package com.bigbear.goodfish.api.controller;

import com.bigbear.goodfish.domain.entity.Message;
import com.bigbear.goodfish.domain.service.AIService;
import com.bigbear.goodfish.domain.service.ChatService;
import com.bigbear.goodfish.domain.service.WebSocketService;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天控制器
 *
 * @author autoagent
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AIService aiService;
    private final WebSocketService webSocketService;

    public ChatController(ChatService chatService, AIService aiService, WebSocketService webSocketService) {
        this.chatService = chatService;
        this.aiService = aiService;
        this.webSocketService = webSocketService;
    }

    /**
     * 获取聊天历史
     */
    @GetMapping("/history")
    public Map<String, Object> getChatHistory(
            @RequestParam String chatId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<Message> messages = chatService.getChatHistory(chatId, limit);

            List<Map<String, ? extends Serializable>> history = messages.stream()
                .map(msg -> Map.of(
                    "id", msg.getId(),
                    "userId", msg.getUserId(),
                    "itemId", msg.getItemId(),
                    "role", msg.getRole(),
                    "content", msg.getContent(),
                    "timestamp", msg.getTimestamp(),
                    "chatId", msg.getChatId()
                ))
                .collect(Collectors.toList());
            // 转换为前端需要的格式

            return Map.of(
                    "success", true,
                    "message", "获取聊天历史成功",
                    "data", history
            );
        } catch (Exception e) {
            log.error("获取聊天历史失败", e);
            return Map.of("success", false, "message", "获取聊天历史失败: " + e.getMessage());
        }
    }

    /**
     * 生成自动回复
     */
    @PostMapping("/reply")
    public Map<String, Object> generateReply(@RequestBody Map<String, Object> request) {
        try {
            String userMessage = (String) request.get("userMessage");
            String itemDescription = (String) request.get("itemDescription");
            String chatId = (String) request.get("chatId");

            if (userMessage == null || chatId == null) {
                return Map.of("success", false, "message", "缺少必要参数");
            }

            // 检查是否处于人工模式
            if (webSocketService.isManualMode(chatId)) {
                return Map.of(
                        "success", true,
                        "manualMode", true,
                        "message", "当前处于人工模式，不生成自动回复"
                );
            }

            // 获取聊天历史
            List<Message> historyMessages = chatService.getChatHistory(chatId, 50);
            List<Map<String, String>> chatHistory = historyMessages.stream()
                    .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                    .collect(Collectors.toList());

            // 检测用户意图
            String intent = aiService.detectIntent(userMessage, itemDescription, aiService.formatHistory(chatHistory));

            // 获取议价次数
            int bargainCount = 0;
            if ("price".equals(intent)) {
                bargainCount = chatService.getBargainCount(chatId);
            }

            // 生成回复
            String reply = aiService.generateReply(intent, userMessage, itemDescription, chatHistory, bargainCount);

            // 如果是议价意图，增加议价次数
            if ("price".equals(intent)) {
                chatService.incrementBargainCount(chatId);
            }

            return Map.of(
                    "success", true,
                    "message", "生成回复成功",
                    "data", Map.of(
                            "reply", reply,
                            "intent", intent
                    )
            );
        } catch (Exception e) {
            log.error("生成回复失败", e);
            return Map.of("success", false, "message", "生成回复失败: " + e.getMessage());
        }
    }

    /**
     * 保存消息
     */
    @PostMapping("/message")
    public Map<String, Object> saveMessage(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String itemId = request.get("itemId");
            String role = request.get("role");
            String content = request.get("content");
            String chatId = request.get("chatId");

            if (userId == null || role == null || content == null) {
                return Map.of("success", false, "message", "缺少必要参数");
            }

            Message message = chatService.saveMessage(userId, itemId, role, content, chatId);

            return Map.of(
                    "success", true,
                    "message", "保存消息成功",
                    "data", Map.of(
                            "id", message.getId(),
                            "timestamp", message.getTimestamp()
                    )
            );
        } catch (Exception e) {
            log.error("保存消息失败", e);
            return Map.of("success", false, "message", "保存消息失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期消息
     */
    @PostMapping("/clean-expired")
    public Map<String, Object> cleanExpiredMessages(
            @RequestBody Map<String, Long> request) {
        try {
            Long expireTime = request.get("expireTime");
            if (expireTime == null) {
                expireTime = 300000L; // 默认5分钟
            }

            chatService.cleanExpiredMessages(expireTime);

            return Map.of(
                    "success", true,
                    "message", "清理过期消息成功"
            );
        } catch (Exception e) {
            log.error("清理过期消息失败", e);
            return Map.of(
                    "success", false,
                    "message", "清理过期消息失败: " + e.getMessage()
            );
        }
    }
}
