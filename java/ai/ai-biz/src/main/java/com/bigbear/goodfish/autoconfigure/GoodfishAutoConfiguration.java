package com.bigbear.goodfish.autoconfigure;

import com.bigbear.aigc.biz.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bigbear.goodfish.client.GoodfishApiClient;
import com.bigbear.goodfish.config.GoodfishConfig;
import com.bigbear.goodfish.domain.AIService;
import com.bigbear.goodfish.service.WebSocketService;
import com.bigbear.goodfish.service.impl.WebSocketServiceImpl;
import com.bigbear.goodfish.websocket.GoodfishWebSocketConfig;
import com.bigbear.goodfish.websocket.GoodfishWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 闲鱼自动回复自动配置类
 * 实现Spring Boot自动配置功能
 *
 * @author autoagent
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GoodfishConfig.class)
@Import(GoodfishWebSocketConfig.class)
public class GoodfishAutoConfiguration {

    /**
     * 创建GoodfishApiClient Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public GoodfishApiClient goodfishApiClient() {
        log.info("创建GoodfishApiClient Bean");
        return new GoodfishApiClient();
    }

    /**
     * 创建WebSocketService Bean
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public WebSocketService webSocketService(GoodfishConfig goodfishConfig, GoodfishApiClient goodfishApiClient) {
        log.info("创建WebSocketService Bean");
        return new WebSocketServiceImpl(goodfishConfig, goodfishApiClient);
    }

    /**
     * 创建GoodfishWebSocketHandler Bean
     */
//    @Bean
//    @ConditionalOnMissingBean
//    public GoodfishWebSocketHandler goodfishWebSocketHandler(ChatService chatService, WebSocketService webSocketService, AIService aiService) {
//        log.info("创建GoodfishWebSocketHandler Bean");
//        return new GoodfishWebSocketHandler(chatService, webSocketService, aiService);
//    }

    /**
     * 创建默认的ObjectMapper Bean（如果不存在）
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 创建默认的AIService Bean（如果不存在）
     * 提供一个空实现，使用时需要自定义实现类
     */
    @Bean
    @ConditionalOnMissingBean
    public AIService aiService() {
        log.info("创建默认AIService Bean（空实现）");
        return new AIService() {
            @Override
            public String detectIntent(String userMessage, String itemDescription, String chatHistory) {
                return "unknown";
            }

            @Override
            public String generateReply(String intent, String userMessage, String itemDescription, java.util.List<java.util.Map<String, String>> chatHistory, int bargainCount) {
                return "您好，我是自动回复助手，暂时无法理解您的问题。请稍后再试。";
            }

            @Override
            public String safeFilter(String text) {
                return text;
            }

            @Override
            public String formatHistory(java.util.List<java.util.Map<String, String>> context) {
                return "";
            }
        };
    }
}
