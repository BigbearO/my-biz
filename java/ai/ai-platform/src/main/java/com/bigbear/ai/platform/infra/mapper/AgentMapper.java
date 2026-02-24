package com.bigbear.ai.platform.infra.mapper;

import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentVo;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 智能体Mapper
 */
@Mapper
public interface AgentMapper extends MPJBaseMapper<AgentEntity>, BaseMapperPlus<AgentEntity, AgentVo> {

    /**
     * 根据智能体编码查询智能体信息
     */
    AgentEntity selectByAgentCode(String agentCode);
}
