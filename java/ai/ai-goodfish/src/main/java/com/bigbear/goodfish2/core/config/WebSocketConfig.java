package com.bigbear.goodfish2.core.config;

import org.springframework.context.annotation.Configuration;

/**
 * WebSocket配置类
 *
 * @author autoagent
 */
@Configuration
public class WebSocketConfig {

    public static final int CONNECT_TIMEOUT = 5000; // 连接超时时间，单位：毫秒
    public static final int MAX_FRAME_SIZE = 10 * 1024 * 1024; // 最大帧大小，单位：字节，默认10MB
    public static final int MAX_MESSAGE_QUEUE_SIZE = 100; // 最大消息队列大小

    /**
     * 获取连接超时时间
     */
    public int getConnectTimeout() {
        return CONNECT_TIMEOUT;
    }

    /**
     * 获取最大帧大小
     */
    public int getMaxFrameSize() {
        return MAX_FRAME_SIZE;
    }

    /**
     * 获取最大消息队列大小
     */
    public int getMaxMessageQueueSize() {
        return MAX_MESSAGE_QUEUE_SIZE;
    }
}
