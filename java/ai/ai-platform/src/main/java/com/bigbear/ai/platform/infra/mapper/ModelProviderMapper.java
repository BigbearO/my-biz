package com.bigbear.ai.platform.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.platform.domain.entity.ModelProviderEntity;
import com.bigbear.ai.platform.domain.vo.ModelProviderVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 模型供应商Mapper
 */
@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity>, BaseMapperPlus<ModelProviderEntity, ModelProviderVo> {

    /**
     * 根据模型类型查询供应商列表
     */
    List<ModelProviderEntity> selectByModelType(String modelType);

    /**
     * 根据供应器编码查询供应商
     */
    ModelProviderEntity selectByProviderCode(String providerCode);
}
