package com.bigbear.goodfish.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 配置Spring WebSocket处理器
 *
 * @author autoagent
 */
@Configuration
@EnableWebSocket
public class GoodfishWebSocketConfig implements WebSocketConfigurer {

    private final GoodfishWebSocketHandler webSocketHandler;

    public GoodfishWebSocketConfig(GoodfishWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器，允许所有来源的跨域请求
        registry.addHandler(webSocketHandler, "/ws/goodfish")
                .setAllowedOrigins("*")
                .withSockJS(); // 启用SockJS后备选项
    }
}
