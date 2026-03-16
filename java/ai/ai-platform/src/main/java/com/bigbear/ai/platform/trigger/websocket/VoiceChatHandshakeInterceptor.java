package com.bigbear.ai.platform.trigger.websocket;

import com.bigbear.ai.platform.domain.vo.AgentContext;
import com.bigbear.ai.platform.infra.router.AgentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket握手拦截器
 */
@Slf4j
@Component
public class VoiceChatHandshakeInterceptor implements HandshakeInterceptor {

    private static final String AGENT_ID_HEADER = "X-Agent-Id";
    private static final String ATTR_AGENT_CONTEXT = "agentContext";

    private final AgentRouter agentRouter;

    public VoiceChatHandshakeInterceptor(AgentRouter agentRouter) {
        this.agentRouter = agentRouter;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        
        // 获取Agent-ID
        String agentId = request.getHeaders().getFirst(AGENT_ID_HEADER);
        
        // 如果Header没有，尝试从Query参数获取
        if (agentId == null || agentId.isEmpty()) {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("agentId=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("agentId=")) {
                        agentId = param.substring(8);
                        break;
                    }
                }
            }
        }
        
        if (agentId == null || agentId.isEmpty()) {
            log.warn("WebSocket握手失败: 缺少Agent-ID");
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        
        // 验证Agent并获取上下文
        AgentContext agentContext = agentRouter.getAgentContext(agentId);
        if (agentContext == null) {
            log.warn("WebSocket握手失败: 无效的Agent-ID: {}", agentId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        
        // 保存Agent上下文到attributes
        attributes.put(ATTR_AGENT_CONTEXT, agentContext);
        
        log.info("WebSocket握手成功: agentId={}", agentId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手后发生错误: {}", exception.getMessage());
        }
    }
    
    /**
     * 从attributes获取AgentContext
     */
    public static AgentContext getAgentContext(Map<String, Object> attributes) {
        return (AgentContext) attributes.get(ATTR_AGENT_CONTEXT);
    }
}
