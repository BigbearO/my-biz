package com.bigbear.goodfish.domain.service;

import java.util.List;
import java.util.Map;

/**
 * AI服务接口
 *
 * @author autoagent
 */
public interface AIService {

    /**
     * 检测用户意图
     */
    String detectIntent(String userMessage, String itemDescription, String chatHistory);

    /**
     * 生成回复
     */
    String generateReply(String intent, String userMessage, String itemDescription, List<Map<String, String>> chatHistory, int bargainCount);

    /**
     * 安全过滤
     */
    String safeFilter(String text);

    /**
     * 格式化对话历史
     */
    String formatHistory(List<Map<String, String>> context);
}
