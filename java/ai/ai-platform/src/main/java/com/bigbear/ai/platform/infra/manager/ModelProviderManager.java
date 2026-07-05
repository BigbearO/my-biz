package com.bigbear.ai.platform.infra.manager;

import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.bigbear.ai.platform.domain.vo.ModelProviderVo;
import com.bigbear.ai.platform.infra.mapper.ModelProviderMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

/**
 * 模型供应商管理服务实现
 */
@Slf4j
@Service
public class ModelProviderManager extends ServiceImpl<ModelProviderMapper, ModelProviderEntity> {

    /**
     * 根据模型类型查询供应商列表
     */
    public List<ModelProviderEntity> listByModelType(String modelType) {
        QueryWrapper qw = QueryWrapper.create().from(ModelProviderEntity.class)
            .where(ModelProviderEntity::getModelType).eq(modelType)
            .and(ModelProviderEntity::getDelFlag).eq(0)
            .orderBy(ModelProviderEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    public TableDataInfo<ModelProviderVo> selectVoPage(PageQuery page, QueryWrapper qw) {
        qw.from(ModelProviderEntity.class);
        Page<ModelProviderEntity> result = mapper.paginate(page.getPageNum(), page.getPageSize(), qw);
        TableDataInfo<ModelProviderVo> dataInfo = new TableDataInfo<>();
        dataInfo.setRows(MapstructUtils.convert(result.getRecords(), ModelProviderVo.class));
        dataInfo.setTotal(result.getTotalRow());
        dataInfo.setCode(200);
        dataInfo.setMsg("查询成功");
        return dataInfo;
    }

    /**
     * 根据供应器编码查询供应商
     */
    public ModelProviderEntity getByProviderCode(String providerCode) {
        QueryWrapper qw = QueryWrapper.create().from(ModelProviderEntity.class)
            .where(ModelProviderEntity::getProviderCode).eq(providerCode)
            .and(ModelProviderEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectOneByQuery(qw);
    }

    /**
     * 查询租户下的供应商列表
     */
    public List<ModelProviderEntity> listByTenantId(Long tenantId) {
        QueryWrapper qw = QueryWrapper.create().from(ModelProviderEntity.class)
            .where(ModelProviderEntity::getTenantId).eq(tenantId)
            .and(ModelProviderEntity::getDelFlag).eq(0)
            .orderBy(ModelProviderEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    /**
     * 软删除供应商
     */
    public boolean softDelete(String id) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return mapper.update(entity)>0;
    }

    /**
     * 检查供应器编码是否已存在
     */
    public boolean isProviderCodeExists(String providerCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(ModelProviderEntity::getProviderCode).eq(providerCode)
            .and(ModelProviderEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectCountByQuery(qw) > 0;
    }

    /**
     * 根据ID查询模型供应商VO
     */
    public ModelProviderVo selectVoById(String id) {
        ModelProviderEntity entity = mapper.selectOneById(id);
        return MapstructUtils.convert(entity, ModelProviderVo.class);
    }
}
