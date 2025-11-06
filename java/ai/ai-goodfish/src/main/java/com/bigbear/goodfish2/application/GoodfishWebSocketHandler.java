package com.bigbear.goodfish2.application;

import com.alibaba.fastjson.JSON;
import com.bigbear.aigc.biz.service.ItemChatService;
import com.bigbear.goodfish2.application.impl.WebSocketServiceImpl;
import com.bigbear.goodfish2.domain.AIService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket处理器
 * 处理WebSocket连接和消息
 *
 * @author autoagent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodfishWebSocketHandler extends TextWebSocketHandler {

    private final ItemChatService chatService;
    private final WebSocketService webSocketService;
    private final AIService aiService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

//    @Autowired
//    public GoodfishWebSocketHandler(ItemChatService chatService, WebSocketService webSocketService, AIService aiService) {
//        this.chatService = chatService;
//        this.webSocketService = webSocketService;
//        this.aiService = aiService;
//    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.put(userId, session);

            // 注册会话到WebSocketService
            ((WebSocketServiceImpl)webSocketService).registerSession(userId, session);
            log.info("WebSocket连接已建立: {}", userId);

            // 发送连接成功消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", "connection-success");
            response.put("message", "连接成功");
            response.put("userId", userId);

            session.sendMessage(new TextMessage(JSON.toJSONString(response)));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        log.info("收到WebSocket消息: userId={}, message={}", userId, message.getPayload());

        try {
            // 解析消息
            Map<String, Object> request = JSON.parseObject(message.getPayload(), Map.class);
            String type = (String) request.get("type");

            // 根据消息类型处理
            switch (type) {
                case "chat":
                    handleChatMessage(session, request);
                    break;
                case "heartbeat":
                    handleHeartbeat(session, userId);
                    break;
                case "refresh-token":
                    handleRefreshToken(session, userId);
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            webSocketService.closeConnection(userId);
            log.info("WebSocket连接已关闭: {}", userId);
        }
    }

    private void handleChatMessage(WebSocketSession session, Map<String, Object> request) throws Exception {

        String chatId = (String) request.get("chatId");
        String content = (String) request.get("content");

        //todo  sass化获取对应的item id 和当前的买家id 来构成chatId

        // 检查是否处于人工模式
        if (webSocketService.isManualMode(chatId)) {
            log.info("处于人工模式，不自动回复: chatId={}", chatId);
            return; // 人工模式下不自动回复
        }


        if (chatService != null) {
            try {

                String reply = chatService.chat(1L,"1",content);
                // 发送回复给客户端
                Map<String, Object> response = new HashMap<>();
                response.put("type", "chat-reply");
                response.put("chatId", chatId);
                response.put("role", "assistant");
                response.put("content", reply);
                response.put("timestamp", System.currentTimeMillis());

                session.sendMessage(new TextMessage(JSON.toJSONString(response)));
            } catch (Exception e) {
                log.error("生成回复失败", e);
                sendErrorResponse(session, "生成回复失败: " + e.getMessage());
            }
        } else {
            log.warn("AI服务未配置，无法生成回复");
        }
    }

    private void handleHeartbeat(WebSocketSession session, String userId) throws Exception {
        // 更新心跳计数
        ((WebSocketServiceImpl)webSocketService).handleHeartbeatResponse(userId);

        // 发送心跳响应
        Map<String, Object> response = new HashMap<>();
        response.put("type", "heartbeat-response");
        response.put("timestamp", System.currentTimeMillis());

        session.sendMessage(new TextMessage(JSON.toJSONString(response)));
    }

    private void handleRefreshToken(WebSocketSession session, String userId) throws Exception {
        String success = webSocketService.refreshToken(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "refresh-token-response");
        response.put("success", success!=null);
        response.put("message", success!=null ? "Token刷新成功" : "Token刷新失败");

        session.sendMessage(new TextMessage(JSON.toJSONString(response)));
    }

    private void handleManualMode(WebSocketSession session, Map<String, Object> request) throws Exception {
        String chatId = (String) request.get("chatId");
        boolean isManual = Boolean.TRUE.equals(request.get("isManual"));

        webSocketService.toggleManualMode(chatId, isManual);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "manual-mode-response");
        response.put("chatId", chatId);
        response.put("isManual", isManual);
        response.put("message", isManual ? "已切换到人工模式" : "已切换到自动模式");

        session.sendMessage(new TextMessage(JSON.toJSONString(response)));
    }

    private void sendErrorResponse(WebSocketSession session, String message) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "error");
        response.put("message", message);

        session.sendMessage(new TextMessage(JSON.toJSONString(response)));
    }
}
