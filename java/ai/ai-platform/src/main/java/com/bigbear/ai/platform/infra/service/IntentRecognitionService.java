package com.bigbear.ai.platform.infra.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务
 * 暂不做实现，留入口
 */
@Slf4j
@Service
public class IntentRecognitionService {

    /**
     * 意图类型
     */
    public enum Intent {
        CHAT,           // 闲聊
        QUESTION,       // 问答
        TASK,           // 任务
        UNKNOWN         // 未知
    }

    /**
     * 识别意图
     * 
     * @param text 输入文本
     * @return 意图类型
     */
    public Intent recognize(String text) {
        // TODO: 实现意图识别
        // 暂时返回闲聊
        log.debug("意图识别: text={}", text);
        return Intent.CHAT;
    }
    
    /**
     * 提取实体
     * 
     * @param text 输入文本
     * @param intent 意图类型
     * @return 实体Map
     */
    public java.util.Map<String, Object> extractEntities(String text, Intent intent) {
        // TODO: 实现实体提取
        return java.util.Collections.emptyMap();
    }
}
