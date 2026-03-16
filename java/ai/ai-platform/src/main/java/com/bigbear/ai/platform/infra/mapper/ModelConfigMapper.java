package com.bigbear.ai.platform.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import com.bigbear.ai.platform.domain.vo.ModelConfigVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 模型配置Mapper
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity>, BaseMapperPlus<ModelConfigEntity, ModelConfigVo> {

    /**
     * 根据模型类型查询配置列表
     */
    List<ModelConfigEntity> selectByModelType(String modelType);

    /**
     * 根据模型编码查询配置
     */
    ModelConfigEntity selectByModelCode(String modelCode);

    /**
     * 根据模型类型查询默认配置
     */
    ModelConfigEntity selectDefaultByModelType(String modelType);
}
