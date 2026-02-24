package com.bigbear.ai.platform.infra.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.bigbear.ai.platform.infra.mapper.ModelProviderMapper;
import com.github.yulichang.base.MPJBaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型供应商管理服务实现
 */
@Slf4j
@Service
public class ModelProviderManager extends MPJBaseServiceImpl<ModelProviderMapper, ModelProviderEntity> {

    /**
     * 根据模型类型查询供应商列表
     */
    public List<ModelProviderEntity> listByModelType(String modelType) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getModelType, modelType)
               .eq(ModelProviderEntity::getDelFlag, 0)
               .orderByAsc(ModelProviderEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 根据供应器编码查询供应商
     */
    public ModelProviderEntity getByProviderCode(String providerCode) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getProviderCode, providerCode)
               .eq(ModelProviderEntity::getDelFlag, 0)
               .last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 查询租户下的供应商列表
     */
    public List<ModelProviderEntity> listByTenantId(Long tenantId) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getTenantId, tenantId)
               .eq(ModelProviderEntity::getDelFlag, 0)
               .orderByAsc(ModelProviderEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 软删除供应商
     */
    public boolean softDelete(String id) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return this.updateById(entity);
    }

    /**
     * 检查供应器编码是否已存在
     */
    public boolean isProviderCodeExists(String providerCode) {
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getProviderCode, providerCode)
               .eq(ModelProviderEntity::getDelFlag, 0)
               .last("limit 1");
        return this.count(wrapper) > 0;
    }
}
