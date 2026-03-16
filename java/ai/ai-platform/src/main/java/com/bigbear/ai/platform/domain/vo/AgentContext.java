package com.bigbear.ai.platform.domain.vo;

import com.bigbear.ai.platform.domain.bo.AgentBo;
import lombok.Data;

import java.util.Map;

/**
 * 智能体上下文
 */
@Data
public class AgentContext {
    
    /**
     * 智能体ID
     */
    private String agentId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 智能体配置
     */
    private AgentBo agentBo;
    
    /**
     * 扩展配置
     */
    private Map<String, Object> ext;
    
    /**
     * 会话ID
     */
    private String sessionId;
}
