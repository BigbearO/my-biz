package com.bigbear.goodfish2.application.impl;

import com.alibaba.fastjson.JSON;
import com.bigbear.goodfish2.application.WebSocketService;
import com.bigbear.goodfish2.core.property.GoodfishConfig;
import com.bigbear.goodfish2.infra.GoodfishApiClient;
import com.bigbear.goodfish2.infra.util.GoodfishUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket服务实现类
 *
 * @author autoagent
 */
@Slf4j
@Service
public class WebSocketServiceImpl implements WebSocketService {

    private final GoodfishConfig goodfishConfig;
    private final GoodfishApiClient goodfishApiClient;

    // 存储用户WebSocket会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // 存储人工模式状态
    private final Map<String, Boolean> manualModeMap = new ConcurrentHashMap<>();
    // 存储心跳计数器
    private final Map<String, Integer> heartbeatCounter = new ConcurrentHashMap<>();
    // 线程池
    private ScheduledExecutorService scheduler;
    //todo map 上限
    // ws-client 存储器


    @Autowired
    public WebSocketServiceImpl(GoodfishConfig goodfishConfig, GoodfishApiClient goodfishApiClient) {
        this.goodfishConfig = goodfishConfig;
        this.goodfishApiClient = goodfishApiClient;
    }

    @Override
    public boolean initConnection(String userId, String token) {
        //1 判断wsClientContainer 存在，不存在就直接返回
        //2 判断close ，关闭了就重新打开
        //3 创建连接，然后put进入 wsClientContainer

        //

        try {
            // 这里简化实现，实际应该建立真正的WebSocket连接
            // 在Spring WebSocket环境中，连接通常由Spring框架管理
            log.info("初始化WebSocket连接: userId={}", userId);

            // 模拟连接成功
            return true;
        } catch (Exception e) {
            log.error("初始化WebSocket连接失败: userId={}", userId, e);
            return false;
        }
    }

    @Override
    public boolean sendMessage(String userId, Map<String, Object> message) {
        try {
            WebSocketSession session = sessions.get(userId);
            if (session != null && session.isOpen()) {
                String messageJson = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(messageJson));
                log.info("发送消息成功: userId={}, message={}", userId, messageJson);
                return true;
            } else {
                log.warn("WebSocket会话不存在或已关闭: userId={}", userId);
                // 尝试重连
                reconnect(userId);
                return false;
            }
        } catch (Exception e) {
            log.error("发送消息失败: userId={}", userId, e);
            return false;
        }
    }

    @Override
    public void closeConnection(String userId) {
        try {
            WebSocketSession session = sessions.remove(userId);
            if (session != null && session.isOpen()) {
                session.close();
            }
            heartbeatCounter.remove(userId);
            log.info("关闭WebSocket连接: userId={}", userId);
        } catch (Exception e) {
            log.error("关闭WebSocket连接失败: userId={}", userId, e);
        }
    }

    @Override
    public String  refreshToken(String userId) {
        try {
            String deviceId = GoodfishUtils.generateDeviceId(userId);
            Map<String, Object> tokenResponse = goodfishApiClient.getToken(deviceId);

            if (tokenResponse != null && tokenResponse.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) tokenResponse.get("data");
                if (data.containsKey("accessToken")) {
                    String newToken = (String) data.get("accessToken");
                    log.info("刷新Token成功: userId={}", userId);
                    return newToken;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("刷新Token失败: userId={}", userId, e);
            return null;
        }
    }

    @Override
    public void toggleManualMode(String chatId, boolean isManual) {
        manualModeMap.put(chatId, isManual);
        log.info("切换人工模式: chatId={}, manual={}", chatId, isManual);
    }

    @Override
    public boolean isManualMode(String chatId) {
        return manualModeMap.getOrDefault(chatId, goodfishConfig.getManual().isDefaultMode());
    }

    @Override
    public void start() {
        scheduler = Executors.newScheduledThreadPool(2);
        // 启动心跳机制
        startHeartbeat();
        // 启动Token刷新
        startTokenRefresh();
        log.info("WebSocket服务已启动");
    }

    @Override
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        // 关闭所有连接
        sessions.forEach((userId, session) -> closeConnection(userId));
        log.info("WebSocket服务已停止");
    }

    @Override
    public boolean isConnected(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 注册WebSocket会话
     */
    public void registerSession(String userId, WebSocketSession session) {
        sessions.put(userId, session);
        heartbeatCounter.put(userId, 0);
        log.info("注册WebSocket会话: userId={}", userId);
    }

    /**
     * 启动心跳机制
     */
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sessions.forEach((userId, session) -> {
                    if (session.isOpen()) {
                        try {
                            // 发送心跳消息
                            Map<String, Object> heartbeatMsg = new HashMap<>();
                            heartbeatMsg.put("type", "heartbeat");
                            heartbeatMsg.put("timestamp", System.currentTimeMillis());
                            String heartbeatJson = JSON.toJSONString(heartbeatMsg);
                            session.sendMessage(new TextMessage(heartbeatJson));

                            // 增加心跳计数
                            int count = heartbeatCounter.getOrDefault(userId, 0) + 1;
                            heartbeatCounter.put(userId, count);

                            // 检查心跳超时
                            if (count > goodfishConfig.getHeartbeat().getMaxMissCount()) {
                                log.warn("心跳超时，关闭连接: userId={}", userId);
                                closeConnection(userId);
                            }
                        } catch (IOException e) {
                            log.error("发送心跳失败: userId={}", userId, e);
                        }
                    }
                });
            } catch (Exception e) {
                log.error("心跳任务执行失败", e);
            }
        }, goodfishConfig.getHeartbeat().getInterval(), goodfishConfig.getHeartbeat().getInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * 启动Token刷新
     */
    private void startTokenRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sessions.keySet().forEach(this::refreshToken);
            } catch (Exception e) {
                log.error("Token刷新任务执行失败", e);
            }
        }, goodfishConfig.getToken().getRefreshInterval(), goodfishConfig.getToken().getRefreshInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * 重连逻辑
     */
    private void reconnect(String userId) {
        try {
            log.info("尝试重连: userId={}", userId);
            // 关闭旧连接
            closeConnection(userId);
            // 重新初始化连接
            initConnection(userId, "");
        } catch (Exception e) {
            log.error("重连失败: userId={}", userId, e);
        }
    }

    /**
     * 处理心跳响应
     */
    public void handleHeartbeatResponse(String userId) {
        heartbeatCounter.put(userId, 0);
    }
}
