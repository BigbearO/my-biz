package com.bigbear.aigc.biz.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.aigc.biz.domain.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 对话记忆Mapper
 */
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {
    /**
     * 根据memoryId和tenantId查询对话记忆
     */
    ChatMemoryEntity selectByMemoryId(@Param("memoryId") String memoryId);
}
