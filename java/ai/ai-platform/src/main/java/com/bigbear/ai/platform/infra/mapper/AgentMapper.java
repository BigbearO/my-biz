package com.bigbear.ai.platform.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentVo;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 智能体Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity>, BaseMapperPlus<AgentEntity, AgentVo> {

    /**
     * 根据智能体编码查询智能体信息
     */
    AgentEntity selectByAgentCode(String agentCode);
}
