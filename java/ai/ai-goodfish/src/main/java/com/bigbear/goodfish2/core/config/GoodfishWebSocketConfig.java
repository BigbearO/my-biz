package com.bigbear.goodfish2.core.config;

import com.bigbear.goodfish2.application.GoodfishWebSocketHandler;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class GoodfishWebSocketConfig implements WebSocketConfigurer {

    private final GoodfishWebSocketHandler webSocketHandler;



    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器，允许所有来源的跨域请求
        registry.addHandler(webSocketHandler, "/ws/goodfish")
                .setAllowedOrigins("*")
                .withSockJS(); // 启用SockJS后备选项
    }
}
