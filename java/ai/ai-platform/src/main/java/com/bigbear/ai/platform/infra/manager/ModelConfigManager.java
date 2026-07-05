package com.bigbear.ai.platform.infra.manager;

import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import com.bigbear.ai.platform.domain.vo.ModelConfigVo;
import com.bigbear.ai.platform.infra.mapper.ModelConfigMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型配置管理服务实现
 */
@Slf4j
@Service
public class ModelConfigManager extends ServiceImpl<ModelConfigMapper, ModelConfigEntity> {

    /**
     * 根据模型类型查询配置列表
     */
    public List<ModelConfigEntity> listByModelType(String modelType) {
        QueryWrapper qw = QueryWrapper.create().from(ModelConfigEntity.class)
            .where(ModelConfigEntity::getModelType).eq(modelType)
            .and(ModelConfigEntity::getDelFlag).eq(0)
            .and(ModelConfigEntity::getIsEnabled).eq(1)
            .orderBy(ModelConfigEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    /**
     * 根据模型编码查询配置
     */
    public ModelConfigEntity getByModelCode(String modelCode) {
        QueryWrapper qw = QueryWrapper.create().from(ModelConfigEntity.class)
            .where(ModelConfigEntity::getModelCode).eq(modelCode)
            .and(ModelConfigEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectOneByQuery(qw);
    }

    /**
     * 查询租户下的配置列表
     */
    public List<ModelConfigEntity> listByTenantId(Long tenantId) {
        QueryWrapper qw = QueryWrapper.create().from(ModelConfigEntity.class)
            .where(ModelConfigEntity::getTenantId).eq(tenantId)
            .and(ModelConfigEntity::getDelFlag).eq(0)
            .orderBy(ModelConfigEntity::getSort, true);
        return mapper.selectListByQuery(qw);
    }

    /**
     * 获取默认配置
     */
    public ModelConfigEntity getDefaultConfig(String modelType) {
        QueryWrapper qw = QueryWrapper.create().from(ModelConfigEntity.class)
            .where(ModelConfigEntity::getModelType).eq(modelType)
            .and(ModelConfigEntity::getIsDefault).eq(1)
            .and(ModelConfigEntity::getIsEnabled).eq(1)
            .and(ModelConfigEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectOneByQuery(qw);
    }

    /**
     * 设置默认配置
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultConfig(String id, String modelType) {
        // 先取消该类型下其他配置的默认状态
        UpdateChain.of(ModelConfigEntity.class)
            .set(ModelConfigEntity::getIsDefault, 0)
            .where(ModelConfigEntity::getModelType).eq(modelType)
            .and(ModelConfigEntity::getIsDefault).eq(1)
            .update();

        // 设置新的默认配置
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setIsDefault(1);
        return mapper.update(entity) >0 ;
    }

    /**
     * 启用/禁用配置
     */
    public boolean toggleEnabled(String id, Integer isEnabled) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setIsEnabled(isEnabled);
        return mapper.update(entity) >0 ;
    }

    /**
     * 软删除配置
     */
    public boolean softDelete(String id) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return mapper.update(entity) >0 ;
    }

    /**
     * 检查模型编码是否已存在
     */
    public boolean isModelCodeExists(String modelCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(ModelConfigEntity::getModelCode).eq(modelCode)
            .and(ModelConfigEntity::getDelFlag).eq(0)
            .limit(1);
        return mapper.selectCountByQuery(qw) > 0;
    }

    /**
     * 分页查询模型配置VO列表
     */
    public TableDataInfo selectVoPage(PageQuery page, QueryWrapper qw) {
        qw.from(ModelConfigEntity.class);
        Page<ModelConfigEntity> result = mapper.paginate(page.getPageNum(), page.getPageSize(), qw);
        TableDataInfo<ModelConfigVo> dataInfo = new TableDataInfo<>();
        dataInfo.setRows(MapstructUtils.convert(result.getRecords(), ModelConfigVo.class));
        dataInfo.setTotal(result.getTotalRow());
        dataInfo.setCode(200);
        dataInfo.setMsg("查询成功");
        return dataInfo;
    }

    /**
     * 根据ID查询模型配置VO
     */
    public ModelConfigVo selectVoById(String id) {
        ModelConfigEntity entity = mapper.selectOneById(id);
        return MapstructUtils.convert(entity, ModelConfigVo.class);
    }
}
