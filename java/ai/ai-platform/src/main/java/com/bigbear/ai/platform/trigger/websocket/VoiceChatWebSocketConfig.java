package com.bigbear.ai.platform.trigger.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置
 */
@Configuration
@EnableWebSocket
public class VoiceChatWebSocketConfig implements WebSocketConfigurer {

    private final VoiceChatWebSocketHandler voiceChatWebSocketHandler;
    private final VoiceChatHandshakeInterceptor handshakeInterceptor;

    public VoiceChatWebSocketConfig(VoiceChatWebSocketHandler voiceChatWebSocketHandler,
                                    VoiceChatHandshakeInterceptor handshakeInterceptor) {
        this.voiceChatWebSocketHandler = voiceChatWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceChatWebSocketHandler, "/ws/voice-chat")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
