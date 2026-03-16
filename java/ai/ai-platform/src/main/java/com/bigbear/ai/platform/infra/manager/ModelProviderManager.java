package com.bigbear.ai.platform.infra.manager;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.ai.platform.domain.bo.ModelProviderBo;
import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.bigbear.ai.platform.domain.vo.ModelProviderVo;
import com.bigbear.ai.platform.infra.mapper.ModelProviderMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
        LambdaQueryWrapper<ModelProviderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelProviderEntity::getModelType, modelType)
               .eq(ModelProviderEntity::getDelFlag, 0)
               .orderByAsc(ModelProviderEntity::getSort);
        return this.list(wrapper);
    }

    public TableDataInfo<ModelProviderVo> selectVoPage(PageQuery page, Wrapper<ModelProviderEntity> lqw) {

        Page<ModelProviderVo> result = baseMapper.selectVoPage(page.build(), lqw);
        return TableDataInfo.build(result);
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

    /**
     * 根据ID查询模型供应商VO
     */
    public ModelProviderVo selectVoById(String id) {
        return baseMapper.selectVoById(id);
    }
}
