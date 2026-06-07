package com.bigbear.ai.ai3d.infra.mapper;

import com.bigbear.ai.ai3d.domain.entity.CheckpointHistoryEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CheckpointHistoryMapper extends BaseMapper<CheckpointHistoryEntity> {
}
