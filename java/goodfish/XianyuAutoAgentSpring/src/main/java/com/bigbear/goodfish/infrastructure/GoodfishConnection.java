package com.bigbear.goodfish.infrastructure;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigbear.goodfish.domain.entity.Account;
import com.bigbear.goodfish.domain.service.GoodFishConnectionService;
import com.bigbear.goodfish.infrastructure.client.GoodFishApiClient;
import com.bigbear.goodfish.infrastructure.config.XianyuConfig;
import com.bigbear.goodfish.infrastructure.util.XianyuUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Data
@Slf4j
@Accessors(chain = true)
public class GoodfishConnection implements GoodFishConnectionService {

    private Account account;

    private XianyuConfig xianyuConfig;
    private GoodFishApiClient goodFishApiClient;

    private final String baseUrl = "wss://wss-goofish.dingtalk.com/";
    private String cookiesStr;
    private Map<String, String> cookies;
    private String myid;
    private String deviceId;
    private String currentToken;
    //todo 心跳机制
    private ReactorNettyWebSocketClient webSocketClient;

    // 心跳相关
    private long lastHeartbeatTime = 0;
    private long lastHeartbeatResponse = 0;
    private ScheduledExecutorService scheduler;

    // 人工接管相关
    private final Set<String> manualModeConversations = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> manualModeTimestamps = new ConcurrentHashMap<>();



    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }



    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(2);
    }


    private void handleMessage(String message) {
        try {
            // 解析消息
            JSONObject jsonMsg = JSON.parseObject(message);

            // 检查是否为心跳响应
            if (jsonMsg.containsKey("rsp") && jsonMsg.getString("rsp").equals("/h")) {
                lastHeartbeatResponse = System.currentTimeMillis();
                return;
            }

            // TODO: 处理聊天消息等其他类型的消息
            log.debug("收到WebSocket消息: {}", message);

        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
        }
    }

    @Override
    public void sendMessage(String cid, String toid, String text) {
        if (webSocketClient == null ) {
            log.error("WebSocket连接未建立，无法发送消息");
            return;
        }

        try {
            // 构建消息内容
            Map<String, Object> content = new HashMap<>();
            content.put("contentType", 1);

            Map<String, String> textContent = new HashMap<>();
            textContent.put("text", text);
            content.put("text", textContent);

            String textBase64 = Base64.getEncoder().encodeToString(JSON.toJSONString(content).getBytes("UTF-8"));

            Map<String, Object> msg = new HashMap<>();
            msg.put("lwp", "/r/MessageSend/sendByReceiverScope");

            Map<String, String> headers = new HashMap<>();
            headers.put("mid", XianyuUtils.generateMid());
            msg.put("headers", headers);

            List<Object> body = new ArrayList<>();

            // 消息主体
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("uuid", XianyuUtils.generateUuid());
            msgBody.put("cid", cid + "@goofish");
            msgBody.put("conversationType", 1);

            Map<String, Object> msgContent = new HashMap<>();
            msgContent.put("contentType", 101);

            Map<String, Object> customContent = new HashMap<>();
            customContent.put("type", 1);
            customContent.put("data", textBase64);
            msgContent.put("custom", customContent);

            msgBody.put("content", msgContent);
            msgBody.put("redPointPolicy", 0);

            Map<String, String> extension = new HashMap<>();
            extension.put("extJson", "{}");
            msgBody.put("extension", extension);

            Map<String, String> ctx = new HashMap<>();
            ctx.put("appVersion", "1.0");
            ctx.put("platform", "web");
            msgBody.put("ctx", ctx);

            msgBody.put("mtags", new HashMap<>());
            msgBody.put("msgReadStatusSetting", 1);

            body.add(msgBody);

            // 接收者信息
            Map<String, Object> receiverBody = new HashMap<>();
            List<String> receivers = new ArrayList<>();
            receivers.add(toid + "@goofish");
            receivers.add(myid + "@goofish");
            receiverBody.put("actualReceivers", receivers);

            body.add(receiverBody);
            msg.put("body", body);

            //todo send
            // 发送消息
//            webSocketClient.send(JSON.toJSONString(msg));
            log.debug("已发送消息到: {}, 内容: {}", toid, text);

        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }



    @Override
    public void close() {
        if (webSocketClient != null) {
            webSocketClient = null;
        }
    }

    @Override
    public String refreshToken() {
        try {
            log.info("开始刷新token...");

            // 调用API获取新token
            Map<String, Object> tokenResult = goodFishApiClient.getToken(deviceId);

            if (tokenResult != null && tokenResult.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) tokenResult.get("data");
                if (data.containsKey("accessToken")) {
                    currentToken = (String) data.get("accessToken");
                    log.info("Token刷新成功");
                    return currentToken;
                }
            }

            log.error("Token刷新失败: {}", tokenResult);
            return null;

        } catch (Exception e) {
            log.error("Token刷新异常", e);
            return null;
        }
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (webSocketClient != null   ) {
                    long currentTime = System.currentTimeMillis();

                    // 检查是否需要发送心跳
                    if (currentTime - lastHeartbeatTime >= xianyuConfig.getHeartbeat().getInterval() * 1000) {
                        Map<String, Object> heartbeatMsg = new HashMap<>();
                        heartbeatMsg.put("lwp", "/h");
                        Map<String, String> headers = new HashMap<>();
                        headers.put("mid", XianyuUtils.generateMid());
                        heartbeatMsg.put("headers", headers);
                        // todo send
//                        webSocketClient.send(JSON.toJSONString(heartbeatMsg));
                        lastHeartbeatTime = currentTime;
                    }

                    // 检查心跳超时
                    if (lastHeartbeatResponse > 0 && currentTime - lastHeartbeatResponse > xianyuConfig.getHeartbeat().getTimeout() * 1000) {
                        log.warn("心跳超时，重新连接");
                        reconnect();
                    }
                }
            } catch (Exception e) {
                log.error("心跳任务执行失败", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void startTokenRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String newToken = refreshToken();
                if (newToken != null && webSocketClient != null ) {
                    log.info("Token已更新，重新建立连接");
                    reconnect();
                }
            } catch (Exception e) {
                log.error("Token刷新任务执行失败", e);
            }
        }, xianyuConfig.getToken().getRefreshInterval(), xianyuConfig.getToken().getRefreshInterval(), TimeUnit.SECONDS);
    }

    private void reconnect() {
        if (webSocketClient != null) {
            //todo 检查连接
            return;
        }

        try {
            Thread.sleep(1000);
            //todo 需要连接服务端
            webSocketClient = new ReactorNettyWebSocketClient();

        } catch (Exception e) {
            log.error("WebSocket重连失败", e);
        }
    }

    @Override
    public void toggleManualMode(String conversationId) {
        if (manualModeConversations.contains(conversationId)) {
            manualModeConversations.remove(conversationId);
            manualModeTimestamps.remove(conversationId);
            log.info("已关闭会话 {} 的人工模式", conversationId);
        } else {
            manualModeConversations.add(conversationId);
            manualModeTimestamps.put(conversationId, LocalDateTime.now());
            log.info("已开启会话 {} 的人工模式", conversationId);
        }
    }

    @Override
    public boolean isManualMode(String conversationId) {
        if (!manualModeConversations.contains(conversationId)) {
            return false;
        }

        // 检查人工模式是否超时
        LocalDateTime timestamp = manualModeTimestamps.get(conversationId);
        if (timestamp != null) {
            long minutes = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now());
            if (minutes > xianyuConfig.getManual().getModeTimeout()) {
                manualModeConversations.remove(conversationId);
                manualModeTimestamps.remove(conversationId);
                log.info("会话 {} 的人工模式已超时", conversationId);
                return false;
            }
        }

        return true;
    }

 }
