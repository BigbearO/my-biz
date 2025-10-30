package com.xianyu.agent.service;

import java.util.List;
import java.util.Map;

public interface ChatContextService {
    void saveItemInfo(String itemId, Map<String, Object> itemData);
    Map<String, Object> getItemInfo(String itemId);
    void addMessageByChat(String chatId, String userId, String itemId, String role, String content);
    List<Map<String, String>> getContextByChat(String chatId);
    void incrementBargainCountByChat(String chatId);
    int getBargainCountByChat(String chatId);
}
