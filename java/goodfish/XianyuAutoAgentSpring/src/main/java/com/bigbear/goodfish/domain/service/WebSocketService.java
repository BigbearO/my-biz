package com.bigbear.goodfish.domain.service;

/**
 * WebSocket服务接口
 *
 * @author autoagent
 */
public interface WebSocketService {

    /**
     * 初始化WebSocket连接
     */
    void initConnection(String cookiesStr);

    /**
     * 发送消息
     */
    void sendMessage(String cid, String toid, String text);

    /**
     * 关闭连接
     */
    void closeConnection();

    /**
     * 刷新Token
     */
    String refreshToken();

    /**
     * 切换人工模式
     */
    void toggleManualMode(String conversationId);

    /**
     * 检查是否处于人工模式
     */
    boolean isManualMode(String conversationId);
}
