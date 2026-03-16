package com.bigbear.ai.biz.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageHistoryMapper extends BaseMapper<MessageHistoryEntity> {

}
