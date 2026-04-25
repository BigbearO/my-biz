package com.bigbear.ai.ai3d.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.ai3d.domain.entity.MessageHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageHistoryMapper extends BaseMapper<MessageHistoryEntity> {

}
