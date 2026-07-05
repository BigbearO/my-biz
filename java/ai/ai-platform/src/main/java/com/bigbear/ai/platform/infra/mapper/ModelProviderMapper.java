package com.bigbear.ai.platform.infra.mapper;

import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型供应商Mapper
 */
@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity> {

    /**
     * 根据模型类型查询供应商列表
     */
    List<ModelProviderEntity> selectByModelType(String modelType);

    /**
     * 根据供应器编码查询供应商
     */
    ModelProviderEntity selectByProviderCode(String providerCode);
}
