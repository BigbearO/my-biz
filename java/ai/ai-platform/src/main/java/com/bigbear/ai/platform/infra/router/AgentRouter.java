package com.bigbear.ai.platform.infra.router;

import cn.hutool.core.bean.BeanUtil;
import com.bigbear.ai.platform.domain.bo.AgentBo;
import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentContext;
import com.bigbear.ai.platform.infra.cache.AgentServiceCache;
import com.bigbear.ai.platform.infra.manager.AgentManager;
import dev.langchain4j.chain.ConversationalRetrievalChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 智能体路由器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRouter {

    private final AgentServiceCache agentServiceCache;
    private final AgentManager agentManager;

//    public AgentRouter(AgentServiceCache agentServiceCache, AgentManager agentManager) {
//        this.agentServiceCache = agentServiceCache;
//        this.agentManager = agentManager;
//    }

    /**
     * 获取智能体上下文
     *
     * @param agentId 智能体ID
     * @return 智能体上下文
     */
    public AgentContext getAgentContext(String agentId) {
        // 从缓存获取
        AgentContext context = agentServiceCache.get(agentId);
        if (context != null) {
            return context;
        }

        // 从数据库获取
        AgentEntity agentBo = agentManager.getById(agentId);
        if (agentBo == null) {
            log.warn("智能体不存在: {}", agentId);
            return null;
        }
        ConversationalRetrievalChain a=null;
        // 创建上下文
        context = new AgentContext();
        context.setAgentId(agentId);
        context.setAgentBo(BeanUtil.copyProperties(agentBo, AgentBo.class));

        // 缓存
        agentServiceCache.put(agentId, context);

        return context;
    }

    /**
     * 获取智能体服务
     *
     * @param agentId 智能体ID
     * @param serviceType 服务类型
     * @param <T> 服务类型
     * @return 服务实例
     */
    public <T> T getService(String agentId, Class<T> serviceType) {
        AgentContext context = getAgentContext(agentId);
        if (context == null) {
            return null;
        }

        // TODO: 根据服务类型和智能体配置获取对应的服务实例
        return null;
    }

    /**
     * 清除缓存
     *
     * @param agentId 智能体ID
     */
    public void invalidate(String agentId) {
        agentServiceCache.invalidate(agentId);
    }
}
