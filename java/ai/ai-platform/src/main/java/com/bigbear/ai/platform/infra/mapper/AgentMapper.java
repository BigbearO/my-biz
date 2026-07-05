package com.bigbear.ai.platform.infra.mapper;

import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {

    /**
     * 根据智能体编码查询智能体信息
     */
    AgentEntity selectByAgentCode(String agentCode);
}
