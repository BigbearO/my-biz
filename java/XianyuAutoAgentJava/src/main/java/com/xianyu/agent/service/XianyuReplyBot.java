package com.xianyu.agent.service;

import com.xianyu.agent.service.agent.Agent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class XianyuReplyBot {

    @Autowired
    private Agent classifyAgent;
    @Autowired
    private Agent priceAgent;
    @Autowired
    private Agent techAgent;
    @Autowired
    private Agent defaultAgent;

    public String generateReply(String userMessage, List<Map<String, String>> context, Map<String, Object> itemInfo) {
        // 首先进行消息分类
        String category = classifyAgent.generate(userMessage, context, itemInfo);
        
        // 根据分类选择合适的Agent
        switch (category.toLowerCase()) {
            case "price":
                return priceAgent.generate(userMessage, context, itemInfo);
            case "tech":
                return techAgent.generate(userMessage, context, itemInfo);
            case "order":
                // 对于订单消息，可以使用默认Agent，或者专门的订单Agent
                return defaultAgent.generate("用户提到下单相关内容：" + userMessage, context, itemInfo);
            default:
                return defaultAgent.generate(userMessage, context, itemInfo);
        }
    }
}
