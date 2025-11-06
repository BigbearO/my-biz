package com.bigbear.goodfish.domain.service;

import com.bigbear.goodfish.domain.entity.Message;

import java.util.List;

/**
 * 聊天服务接口
 *
 * @author autoagent
 */
public interface ChatService {

    /**
     * 保存消息
     */
    Message saveMessage(String userId, String itemId, String role, String content, String chatId);

    /**
     * 获取聊天历史
     */
    List<Message> getChatHistory(String chatId, int limit);

    /**
     * 获取用户与商品之间的聊天历史
     */
    List<Message> getChatHistoryByUserAndItem(String userId, String itemId, int limit);

    /**
     * 清理过期消息
     */
    void cleanExpiredMessages(long expireTimeInMillis);

    /**
     * 获取议价次数
     */
    int getBargainCount(String chatId);

    /**
     * 增加议价次数
     */
    int incrementBargainCount(String chatId);
}
