package com.bigbear.goodfish.domain;

import java.util.List;
import java.util.Map;

/**
 * AI服务接口
 * 预留接口，用于AI相关功能实现
 *
 * @author autoagent
 */
public interface AIService {

    /**
     * 检测用户意图
     * @param userMessage 用户消息
     * @param itemDescription 商品描述
     * @param chatHistory 聊天历史
     * @return 检测到的意图
     */
    String detectIntent(String userMessage, String itemDescription, String chatHistory);

    /**
     * 生成回复
     * @param intent 意图
     * @param userMessage 用户消息
     * @param itemDescription 商品描述
     * @param chatHistory 聊天历史
     * @param bargainCount 议价次数
     * @return 生成的回复内容
     */
    String generateReply(String intent, String userMessage, String itemDescription, List<Map<String, String>> chatHistory, int bargainCount);

    /**
     * 安全过滤
     * @param text 需要过滤的文本
     * @return 过滤后的文本
     */
    String safeFilter(String text);

    /**
     * 格式化对话历史
     * @param context 对话上下文
     * @return 格式化后的对话历史字符串
     */
    String formatHistory(List<Map<String, String>> context);
}
