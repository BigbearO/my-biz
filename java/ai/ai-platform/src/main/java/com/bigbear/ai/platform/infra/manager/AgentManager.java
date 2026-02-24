package com.bigbear.ai.platform.infra.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.infra.mapper.AgentMapper;
import com.github.yulichang.base.MPJBaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体管理服务实现
 */
@Slf4j
@Service
public class AgentManager extends MPJBaseServiceImpl<AgentMapper, AgentEntity> {

    /**
     * 根据智能体编码查询智能体
     */
    public AgentEntity getByAgentCode(String agentCode) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEntity::getAgentCode, agentCode)
               .eq(AgentEntity::getDelFlag, 0)
               .last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 根据用户ID查询智能体列表
     */
    public List<AgentEntity> listByUserId(Long userId) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEntity::getUserId, userId)
               .eq(AgentEntity::getDelFlag, 0)
               .orderByAsc(AgentEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 查询租户下的智能体列表
     */
    public List<AgentEntity> listByTenantId(Long tenantId) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEntity::getTenantId, tenantId)
               .eq(AgentEntity::getDelFlag, 0)
               .orderByAsc(AgentEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 软删除智能体
     */
    public boolean softDelete(String id) {
        AgentEntity entity = new AgentEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return this.updateById(entity);
    }

    /**
     * 检查智能体编码是否已存在
     */
    public boolean isAgentCodeExists(String agentCode) {
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEntity::getAgentCode, agentCode)
               .eq(AgentEntity::getDelFlag, 0)
               .last("limit 1");
        return this.count(wrapper) > 0;
    }
}
