package com.bigbear.ai.platform.infra.manager;

import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentVo;
import com.bigbear.ai.platform.infra.mapper.AgentMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;

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
        QueryWrapper qw = QueryWrapper.create().from(AgentEntity.class)
            .where(AgentEntity::getAgentCode).eq(agentCode)
            .and(AgentEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectOneByQuery(qw);
    }

    /**
     * 根据用户ID查询智能体列表
     */
    public List<AgentEntity> listByUserId(Long userId) {
        QueryWrapper qw = QueryWrapper.create().from(AgentEntity.class)
            .where(AgentEntity::getUserId).eq(userId)
            .and(AgentEntity::getDelFlag).eq(0)
            .orderBy(AgentEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    /**
     * 查询租户下的智能体列表
     */
    public List<AgentEntity> listByTenantId(Long tenantId) {
        QueryWrapper qw = QueryWrapper.create().from(AgentEntity.class)
            .where(AgentEntity::getTenantId).eq(tenantId)
            .and(AgentEntity::getDelFlag).eq(0)
            .orderBy(AgentEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    /**
     * 软删除智能体
     */
    public boolean softDelete(String id) {
        AgentEntity entity = new AgentEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return mapper.update(entity)>0;
    }

    /**
     * 检查智能体编码是否已存在
     */
    public boolean isAgentCodeExists(String agentCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(AgentEntity::getAgentCode).eq(agentCode)
            .and(AgentEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectCountByQuery(qw) > 0;
    }

    /**
     * 分页查询智能体VO列表
     */
    public PageResult selectVoPage(PageQuery page, QueryWrapper qw) {
        qw.from(AgentEntity.class);
        Page<AgentEntity> result = mapper.paginate(page.getPageNum(), page.getPageSize(), qw);
        PageResult<AgentVo> dataInfo = new PageResult<>();
        dataInfo.setRows(MapstructUtils.convert(result.getRecords(), AgentVo.class));
        dataInfo.setTotal(result.getTotalRow());

        return dataInfo;
    }

    /**
     * 根据ID查询智能体VO
     */
    public AgentVo selectVoById(String id) {
        AgentEntity entity = mapper.selectOneById(id);
        return MapstructUtils.convert(entity, AgentVo.class);
    }
}
