package com.bigbear.goodfish2.application;

import java.util.Map;

/**
 * WebSocket服务接口
 *
 * @author autoagent
 */
public interface WebSocketService {


    /**
     * 初始化WebSocket连接
     * @param userId 用户ID
     * @param token 认证token
     * @return 是否连接成功
     * todo 应该是获取了item，从而关联的账户-再传入商品id和token，监听一个用户的，再从消息里面解析商品信息
     *
     */
    boolean initConnection(String userId, String token);

    /**
     * 发送消息
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(String userId, Map<String, Object> message);

    /**
     * 关闭WebSocket连接
     * @param userId 用户ID
     */
    void closeConnection(String userId);

    /**
     * 刷新Token
     * @param userId 用户ID
     * @return 是否刷新成功
     */
    String refreshToken(String userId);

    /**
     * 切换人工模式
     * @param chatId 聊天ID
     * @param isManual 是否为人工模式
     */
    void toggleManualMode(String chatId, boolean isManual);

    /**
     * 检查是否为人工模式
     * @param chatId 聊天ID
     * @return 是否为人工模式
     */
    boolean isManualMode(String chatId);

    /**
     * 启动服务
     */
    void start();

    /**
     * 停止服务
     */
    void stop();

    /**
     * 检查连接状态
     * @param userId 用户ID
     * @return 连接是否有效
     */
    boolean isConnected(String userId);
}
