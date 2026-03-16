package com.bigbear.ai.biz.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.ai.biz.domain.entity.ChatMemoryEntity;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {

}
