package com.bigbear.ai.platform.infra.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import com.bigbear.ai.platform.infra.mapper.ModelConfigMapper;
import com.github.yulichang.base.MPJBaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型配置管理服务实现
 */
@Slf4j
@Service
public class ModelConfigManager extends MPJBaseServiceImpl<ModelConfigMapper, ModelConfigEntity> {

    /**
     * 根据模型类型查询配置列表
     */
    public List<ModelConfigEntity> listByModelType(String modelType) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getModelType, modelType)
               .eq(ModelConfigEntity::getDelFlag, 0)
               .eq(ModelConfigEntity::getIsEnabled, 1)
               .orderByAsc(ModelConfigEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 根据模型编码查询配置
     */
    public ModelConfigEntity getByModelCode(String modelCode) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getModelCode, modelCode)
               .eq(ModelConfigEntity::getDelFlag, 0)
               .last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 查询租户下的配置列表
     */
    public List<ModelConfigEntity> listByTenantId(Long tenantId) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getTenantId, tenantId)
               .eq(ModelConfigEntity::getDelFlag, 0)
               .orderByAsc(ModelConfigEntity::getSort);
        return this.list(wrapper);
    }

    /**
     * 获取默认配置
     */
    public ModelConfigEntity getDefaultConfig(String modelType) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getModelType, modelType)
               .eq(ModelConfigEntity::getIsDefault, 1)
               .eq(ModelConfigEntity::getIsEnabled, 1)
               .eq(ModelConfigEntity::getDelFlag, 0)
               .last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * 设置默认配置
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultConfig(String id, String modelType) {
        // 先取消该类型下其他配置的默认状态
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getModelType, modelType)
               .eq(ModelConfigEntity::getIsDefault, 1);
        ModelConfigEntity resetEntity = new ModelConfigEntity();
        resetEntity.setIsDefault(0);
        this.update(resetEntity, wrapper);

        // 设置新的默认配置
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setIsDefault(1);
        return this.updateById(entity);
    }

    /**
     * 启用/禁用配置
     */
    public boolean toggleEnabled(String id, Integer isEnabled) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setIsEnabled(isEnabled);
        return this.updateById(entity);
    }

    /**
     * 软删除配置
     */
    public boolean softDelete(String id) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setDelFlag(1);
        return this.updateById(entity);
    }

    /**
     * 检查模型编码是否已存在
     */
    public boolean isModelCodeExists(String modelCode) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getModelCode, modelCode)
               .eq(ModelConfigEntity::getDelFlag, 0)
               .last("limit 1");
        return this.count(wrapper) > 0;
    }
}
