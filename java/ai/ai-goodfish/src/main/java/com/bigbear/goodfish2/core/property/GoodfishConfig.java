package com.bigbear.goodfish2.core.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 闲鱼自动回复配置类
 *
 * @author autoagent
 */
@Data
@Component
@ConfigurationProperties(prefix = "goodfish")
public class GoodfishConfig {

    /**
     * 心跳配置
     */
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * Token配置
     */
    private Token token = new Token();

    /**
     * 人工模式配置
     */
    private Manual manual = new Manual();

    /**
     * 消息配置
     */
    private Message message = new Message();

    @Data
    public static class Heartbeat {
        private long interval = 30000; // 默认30秒
        private int maxMissCount = 3;
    }

    @Data
    public static class Token {
        private long refreshInterval = 3600000; // 默认1小时
        private int maxRetryCount = 3;
    }

    @Data
    public static class Manual {
        private boolean defaultMode = false;
    }

    @Data
    public static class Message {
        private int maxLength = 500;
    }
}
