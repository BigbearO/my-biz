package com.bigbear.goodfish.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bigbear.goodfish.domain.service.AIService;
import com.bigbear.goodfish.domain.service.ChatService;
import com.bigbear.goodfish.domain.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket处理器
 *
 * @author autoagent
 */
@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final AIService aiService;
    private final WebSocketService webSocketService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketHandler(ObjectMapper objectMapper, ChatService chatService, AIService aiService, WebSocketService webSocketService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.aiService = aiService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket连接已建立: {}", userId);

            // 发送连接成功消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", "connection-success");
            response.put("message", "连接成功");
            response.put("userId", userId);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        log.info("收到WebSocket消息: userId={}, message={}", userId, message.getPayload());

        try {
            // 解析消息
            Map<String, Object> request = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) request.get("type");

            // 根据消息类型处理
            switch (type) {
                case "chat":
                    handleChatMessage(session, request);
                    break;
                case "heartbeat":
                    handleHeartbeat(session);
                    break;
                case "refresh-token":
                    handleRefreshToken(session);
                    break;
                case "manual-mode":
                    handleManualMode(session, request);
                    break;
                default:
                    sendErrorResponse(session, "未知消息类型");
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            sendErrorResponse(session, "处理消息失败: " + e.getMessage());
        }
    }

    private void handleChatMessage(WebSocketSession session, Map<String, Object> request) throws Exception {
        String chatId = (String) request.get("chatId");
        String itemId = (String) request.get("itemId");
        String content = (String) request.get("content");
        String userRole = (String) request.get("role");
        String userId = (String) request.get("userId");

        // 保存用户消息
        chatService.saveMessage(userId, itemId, userRole, content, chatId);

        // 检查是否处于人工模式
        if (webSocketService.isManualMode(chatId)) {
            return; // 人工模式下不自动回复
        }

        // 获取聊天历史
        var history = chatService.getChatHistory(chatId, 50);
        var chatHistory = history.stream()
                .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                .toList();

        // 假设我们已经有了商品描述
        String itemDescription = "";
        try {
            // 这里可以从商品服务获取商品描述
            // itemDescription = itemService.getItemById(itemId).getDescription();
        } catch (Exception e) {
            log.warn("获取商品描述失败", e);
        }

        // 检测用户意图
        String intent = aiService.detectIntent(content, itemDescription, aiService.formatHistory(chatHistory));

        // 获取议价次数
        int bargainCount = 0;
        if ("price".equals(intent)) {
            bargainCount = chatService.getBargainCount(chatId);
        }

        // 生成回复
        String reply = aiService.generateReply(intent, content, itemDescription, chatHistory, bargainCount);

        // 如果是议价意图，增加议价次数
        if ("price".equals(intent)) {
            chatService.incrementBargainCount(chatId);
        }

        // 保存AI回复
        chatService.saveMessage("system", itemId, "assistant", reply, chatId);

        // 发送回复给客户端
        Map<String, Object> response = new HashMap<>();
        response.put("type", "chat-reply");
        response.put("chatId", chatId);
        response.put("itemId", itemId);
        response.put("role", "assistant");
        response.put("content", reply);
        response.put("timestamp", System.currentTimeMillis());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleHeartbeat(WebSocketSession session) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "heartbeat-response");
        response.put("timestamp", System.currentTimeMillis());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleRefreshToken(WebSocketSession session) throws Exception {
        try {
            String newToken = webSocketService.refreshToken();

            Map<String, Object> response = new HashMap<>();
            response.put("type", "token-refreshed");
            response.put("token", newToken);
            response.put("timestamp", System.currentTimeMillis());

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("刷新Token失败", e);
            sendErrorResponse(session, "刷新Token失败: " + e.getMessage());
        }
    }

    private void handleManualMode(WebSocketSession session, Map<String, Object> request) throws Exception {
        String chatId = (String) request.get("chatId");

        if (chatId == null) {
            sendErrorResponse(session, "缺少chatId参数");
            return;
        }

        // 切换人工模式
        webSocketService.toggleManualMode(chatId);
        boolean isManualMode = webSocketService.isManualMode(chatId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "manual-mode-updated");
        response.put("chatId", chatId);
        response.put("manualMode", isManualMode);
        response.put("timestamp", System.currentTimeMillis());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void sendErrorResponse(WebSocketSession session, String message) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket连接已关闭: {}", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        log.error("WebSocket传输错误: userId={}", userId, exception);

        // 尝试关闭会话
        if (session.isOpen()) {
            session.close();
        }

        // 从会话列表中移除
        if (userId != null) {
            sessions.remove(userId);
        }
    }
}
