package com.bigbear.ai.platform.infra.manager;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.ai.platform.domain.bo.AgentBo;
import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentVo;
import com.bigbear.ai.platform.infra.mapper.AgentMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

/**
 * 智能体管理服务实现
 */
@Slf4j
@Service
public class AgentManager extends ServiceImpl<AgentMapper, AgentEntity> {

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

    /**
     * 分页查询智能体VO列表
     */
    public TableDataInfo selectVoPage(PageQuery page, Wrapper<AgentEntity> lqw) {
        Page<AgentVo> result = baseMapper.selectVoPage(page.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 根据ID查询智能体VO
     */
    public AgentVo selectVoById(String id) {
        return baseMapper.selectVoById(id);
    }
}
