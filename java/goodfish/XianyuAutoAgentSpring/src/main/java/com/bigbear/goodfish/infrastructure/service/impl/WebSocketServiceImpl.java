package com.bigbear.goodfish.infrastructure.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bigbear.goodfish.domain.service.WebSocketService;
import com.bigbear.goodfish.infrastructure.client.GoodFishApiClient;
import com.bigbear.goodfish.infrastructure.config.XianyuConfig;
import com.bigbear.goodfish.infrastructure.util.XianyuUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket服务实现
 *
 * @author autoagent
 */
@Slf4j
@Service
public class WebSocketServiceImpl implements WebSocketService {

    private final XianyuConfig xianyuConfig;
    private final GoodFishApiClient goodFishApiClient;

    private final String   baseUrl = "wss://wss-goofish.dingtalk.com/";
    private String cookiesStr;
    private Map<String, String> cookies;
    private String myid;
    private String deviceId;
    private String currentToken;
    private WebSocketClient webSocketClient;

    // 心跳相关
    private long lastHeartbeatTime = 0;
    private long lastHeartbeatResponse = 0;
    private ScheduledExecutorService scheduler;

    // 人工接管相关
    private final Set<String> manualModeConversations = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> manualModeTimestamps = new ConcurrentHashMap<>();

    //ws只能监听账号级别的，接受到消息之后再获取商品信息进行处理，key是userId,暂定userId是闲鱼的账号id,v 一个socketService，不在spring管理
    private final Map<String, Object> accountSocketMap = new ConcurrentHashMap<>();

    public WebSocketServiceImpl(XianyuConfig xianyuConfig, GoodFishApiClient goodFishApiClient) {
        this.xianyuConfig = xianyuConfig;
        this.goodFishApiClient = goodFishApiClient;
    }



    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        closeConnection();
    }

    @Override
    public void initConnection(String cookiesStr) {
        this.cookiesStr = cookiesStr;
        // 构建 account
        // todo-x 这些个之后再说回去用 那个改好了的复制一下
        this.cookies = XianyuUtils.transCookies(cookiesStr);
        this.myid = cookies.get("unb");
        this.deviceId = XianyuUtils.generateDeviceId(myid);

        // 获取初始token
        // todo-x，改造成 socket 内部的方法
        refreshToken();

        // 建立WebSocket连接
        try {
            webSocketClient = new WebSocketClient(new URI(baseUrl)) {
                @Override
                public void onOpen(Object handshakedata) {
                    log.info("WebSocket连接已建立");
                    init2  ();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket连接已关闭: {}", reason);
                    // 尝试重连
                    if (!remote) {
                        reconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket连接错误", ex);
                }
            };

            webSocketClient.connect();

            // 启动心跳任务
            startHeartbeat();

            // 启动Token刷新任务
            startTokenRefresh();

        } catch (URISyntaxException e) {
            log.error("WebSocket连接初始化失败", e);
            throw new RuntimeException("WebSocket连接初始化失败", e);
        }
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(2);
    }
    public void init2() {


        if (webSocketClient == null || !webSocketClient.isOpen()) {
            log.error("WebSocket连接未建立，无法初始化");
            return;
        }

        // 发送注册消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("lwp", "/reg");

        Map<String, String> headers = new HashMap<>();
        headers.put("cache-header", "app-key token ua wv");
        headers.put("app-key", "444e9908a51d1cb236a27862abc769c9");
        headers.put("token", currentToken);
        headers.put("ua", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 DingTalk(2.1.5) OS(Windows/10) Browser(Chrome/133.0.0.0) DingWeb/2.1.5 IMPaaS DingWeb/2.1.5");
        headers.put("dt", "j");
        headers.put("wv", "im:3,au:3,sy:6");
        headers.put("sync", "0,0;0;0;");
        headers.put("did", deviceId);
        headers.put("mid", XianyuUtils.generateMid());

        msg.put("headers", headers);

        webSocketClient.send(JSON.toJSONString(msg));
        log.info("WebSocket连接注册完成");

        // 发送同步状态确认消息
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> syncMsg = new HashMap<>();
        syncMsg.put("lwp", "/r/SyncStatus/ackDiff");

        Map<String, String> syncHeaders = new HashMap<>();
        syncHeaders.put("mid", "5701741704675979 0");
        syncMsg.put("headers", syncHeaders);

        List<Object> syncBody = new ArrayList<>();
        Map<String, Object> syncBodyItem = new HashMap<>();
        syncBodyItem.put("pipeline", "sync");
        syncBodyItem.put("tooLong2Tag", "PNM,1");
        syncBodyItem.put("channel", "sync");
        syncBodyItem.put("topic", "sync");
        syncBodyItem.put("highPts", 0);
        syncBodyItem.put("pts", System.currentTimeMillis() * 1000);
        syncBodyItem.put("seq", 0);
        syncBodyItem.put("timestamp", System.currentTimeMillis());
        syncBody.add(syncBodyItem);

        syncMsg.put("body", syncBody);
        webSocketClient.send(JSON.toJSONString(syncMsg));
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
        if (webSocketClient == null || !webSocketClient.isOpen()) {
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

            // 发送消息
            webSocketClient.send(JSON.toJSONString(msg));
            log.debug("已发送消息到: {}, 内容: {}", toid, text);

        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    @Override
    public void closeConnection() {
        if (webSocketClient != null) {
            webSocketClient.close();
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
                if (webSocketClient != null && webSocketClient.isOpen()) {
                    long currentTime = System.currentTimeMillis();

                    // 检查是否需要发送心跳
                    if (currentTime - lastHeartbeatTime >= xianyuConfig.getHeartbeat().getInterval() * 1000) {
                        Map<String, Object> heartbeatMsg = new HashMap<>();
                        heartbeatMsg.put("lwp", "/h");
                        Map<String, String> headers = new HashMap<>();
                        headers.put("mid", XianyuUtils.generateMid());
                        heartbeatMsg.put("headers", headers);

                        webSocketClient.send(JSON.toJSONString(heartbeatMsg));
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
                if (newToken != null && webSocketClient != null && webSocketClient.isOpen()) {
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
            webSocketClient.close();
        }

        try {
            Thread.sleep(1000);
            webSocketClient = new WebSocketClient(new URI(baseUrl)) {


                @Override
                public void onOpen(Object handshakedata) {

                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket连接已关闭: {}", reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket连接错误", ex);
                }
            };

            webSocketClient.connect();

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

    // 注意：这里简化了WebSocketClient的实现，实际项目中应使用适当的WebSocket客户端库
    private abstract class WebSocketClient {
        private final URI serverUri;
        private boolean open = false;

        public WebSocketClient(URI serverUri) {
            this.serverUri = serverUri;
        }

        public abstract void onOpen(Object handshakedata);
        public abstract void onMessage(String message);
        public abstract void onClose(int code, String reason, boolean remote);
        public abstract void onError(Exception ex);

        public void connect() {
            // 简化实现，实际项目中应使用真实的WebSocket连接
            log.info("正在连接到WebSocket服务器: {}", serverUri);
            // 模拟连接成功
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    open = true;
                    onOpen(null);
                } catch (Exception e) {
                    onError(e);
                }
            }).start();
        }

        public void send(String text) {
            if (open) {
                log.info("发送WebSocket消息: {}", text);
                // 简化实现，实际项目中应发送真实的WebSocket消息
            }
        }

        public void close() {
            open = false;
            log.info("关闭WebSocket连接");
            // 简化实现，实际项目中应关闭真实的WebSocket连接
        }

        public boolean isOpen() {
            return open;
        }
    }
}
