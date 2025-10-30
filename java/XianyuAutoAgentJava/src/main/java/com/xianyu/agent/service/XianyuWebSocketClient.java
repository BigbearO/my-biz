package com.xianyu.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.xianyu.agent.util.XianyuUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class XianyuWebSocketClient {

    @Value("${xianyu.websocket.url}")
    private String wsUrl;

    @Value("${xianyu.token.refresh-interval:3600000}")
    private long tokenRefreshInterval;

    @Value("${xianyu.heartbeat.interval:30000}")
    private long heartbeatInterval;

    @Value("${xianyu.manual-mode.timeout:300000}")
    private long manualModeTimeout;

    @Autowired
    private XianyuApis xianyuApis;
    @Autowired
    private XianyuReplyBot xianyuReplyBot;
    @Autowired
    private ChatContextService chatContextService;
    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketClient wsClient;
    private String token;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isManualMode = new AtomicBoolean(false);
    private final AtomicLong lastManualModeTime = new AtomicLong(0);
    private final Map<String, Long> manualModeChats = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void connect() throws Exception {
        URI uri = new URI(wsUrl + "?token=" + token);
        wsClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("WebSocket连接已建立");
                isConnected.set(true);
                // 发送初始化包
                sendInitPackage();
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("WebSocket连接已关闭: " + reason);
                isConnected.set(false);
                reconnect();
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("WebSocket错误: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        wsClient.connect();
    }

    private void sendInitPackage() {
        try {
            Map<String, Object> initData = new HashMap<>();
            initData.put("type", "sync");
            initData.put("mid", XianyuUtils.generateMid());
            initData.put("deviceId", XianyuUtils.generateDeviceId());
            
            wsClient.send(objectMapper.writeValueAsString(initData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String chatId, String userId, String message) {
        try {
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("type", "chat");
            msgData.put("chatId", chatId);
            msgData.put("userId", userId);
            msgData.put("content", message);
            msgData.put("timestamp", System.currentTimeMillis());
            
            wsClient.send(objectMapper.writeValueAsString(msgData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String type = data.get("type").toString();
            
            if (isChatMessage(type)) {
                handleChatMessage(data);
            } else if (isSyncPackage(type)) {
                handleSyncPackage(data);
            } else if (isHeartbeat(type)) {
                handleHeartbeat();
            } else if (isSystemMessage(type)) {
                handleSystemMessage(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleChatMessage(Map<String, Object> data) {
        try {
            String chatId = data.get("chatId").toString();
            String userId = data.get("userId").toString();
            String content = data.get("content").toString();
            String itemId = data.getOrDefault("itemId", "").toString();
            
            // 检查是否需要进入人工模式
            if (checkToggleKeywords(content)) {
                toggleManualMode(chatId);
                return;
            }
            
            // 检查是否处于人工模式
            if (isManualModeForChat(chatId)) {
                return;
            }
            
            // 获取商品信息
            Map<String, Object> itemInfo = chatContextService.getItemInfo(itemId);
            if (itemInfo.isEmpty()) {
                itemInfo = xianyuApis.getItemInfo(itemId);
                if (!itemInfo.isEmpty()) {
                    chatContextService.saveItemInfo(itemId, itemInfo);
                }
            }
            
            // 保存用户消息
            chatContextService.addMessageByChat(chatId, userId, itemId, "user", content);
            
            // 获取对话历史
            List<Map<String, String>> context = chatContextService.getContextByChat(chatId);
            
            // 检查是否为议价消息
            if (XianyuUtils.isBargainMessage(content)) {
                chatContextService.incrementBargainCountByChat(chatId);
                int bargainCount = chatContextService.getBargainCountByChat(chatId);
                // 可以根据议价次数调整回复策略
            }
            
            // 生成回复
            String reply = xianyuReplyBot.generateReply(content, context, itemInfo);
            
            // 保存回复并发送
            chatContextService.addMessageByChat(chatId, "bot", itemId, "assistant", reply);
            sendMessage(chatId, userId, reply);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSyncPackage(Map<String, Object> data) {
        // 处理同步包逻辑
        System.out.println("收到同步包: " + data);
    }

    private void handleHeartbeat() {
        // 发送心跳响应
        try {
            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("type", "heartbeat");
            heartbeat.put("timestamp", System.currentTimeMillis());
            wsClient.send(objectMapper.writeValueAsString(heartbeat));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSystemMessage(Map<String, Object> data) {
        // 处理系统消息
        System.out.println("收到系统消息: " + data);
    }

    @Scheduled(fixedRateString = "${xianyu.heartbeat.interval:30000}")
    public void sendHeartbeat() {
        if (isConnected.get() && wsClient != null && wsClient.isOpen()) {
            try {
                Map<String, Object> heartbeat = new HashMap<>();
                heartbeat.put("type", "heartbeat");
                heartbeat.put("timestamp", System.currentTimeMillis());
                wsClient.send(objectMapper.writeValueAsString(heartbeat));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Scheduled(fixedRateString = "${xianyu.token.refresh-interval:3600000}")
    public void refreshToken() {
        String newToken = xianyuApis.refreshToken();
        if (newToken != null) {
            token = newToken;
            // 重新连接WebSocket
            reconnect();
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkManualModeTimeout() {
        long now = System.currentTimeMillis();
        manualModeChats.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > manualModeTimeout) {
                System.out.println("聊天 " + entry.getKey() + " 人工模式超时，自动切换回自动模式");
                return true;
            }
            return false;
        });
    }

    private void reconnect() {
        try {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.close();
            }
            Thread.sleep(2000);
            connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isChatMessage(String type) {
        return "chat".equals(type);
    }

    private boolean isSyncPackage(String type) {
        return "sync".equals(type);
    }

    private boolean isHeartbeat(String type) {
        return "heartbeat".equals(type);
    }

    private boolean isSystemMessage(String type) {
        return "system".equals(type);
    }

    private boolean checkToggleKeywords(String message) {
        String[] toggleWords = {"人工", "接管", "切换", "auto", "manual"};
        for (String word : toggleWords) {
            if (message.toLowerCase().contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void toggleManualMode(String chatId) {
        long now = System.currentTimeMillis();
        if (manualModeChats.containsKey(chatId)) {
            manualModeChats.remove(chatId);
            System.out.println("聊天 " + chatId + " 切换为自动模式");
        } else {
            manualModeChats.put(chatId, now);
            System.out.println("聊天 " + chatId + " 切换为人工模式");
        }
    }

    private boolean isManualModeForChat(String chatId) {
        return manualModeChats.containsKey(chatId);
    }

    @PreDestroy
    public void destroy() {
        if (wsClient != null) {
            wsClient.close();
        }
    }
}
