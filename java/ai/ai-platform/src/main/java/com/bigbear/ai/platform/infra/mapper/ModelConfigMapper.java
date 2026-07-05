package com.bigbear.ai.platform.infra.mapper;

import com.bigbear.ai.platform.domain.entity.ModelConfigEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置Mapper
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {

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
