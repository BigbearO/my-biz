package com.xianyu.agent.service.agent;

import java.util.List;
import java.util.Map;

public interface Agent {
    String generate(String userMessage, List<Map<String, String>> context, Map<String, Object> itemInfo);
}
