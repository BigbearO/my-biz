package com.bigbear.goodfish.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 闲鱼配置类
 *
 * @author autoagent
 */
@Data
@Component
@ConfigurationProperties(prefix = "xianyu")
public class XianyuConfig {

    private Heartbeat heartbeat = new Heartbeat();
    private Token token = new Token();
    private Manual manual = new Manual();
    private Message message = new Message();
    private String toggleKeywords = "。";

    @Data
    public static class Heartbeat {
        private int interval = 15;
        private int timeout = 5;
    }

    @Data
    public static class Token {
        private int refreshInterval = 3600;
        private int retryInterval = 300;
    }

    @Data
    public static class Manual {
        private int modeTimeout = 3600;
    }

    @Data
    public static class Message {
        private int expireTime = 300000;
    }
}
