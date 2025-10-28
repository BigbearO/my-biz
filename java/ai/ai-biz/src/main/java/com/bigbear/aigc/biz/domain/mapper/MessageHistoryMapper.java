package com.bigbear.aigc.biz.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.aigc.biz.domain.entity.MessageHistoryEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息历史Mapper
 */
public interface MessageHistoryMapper extends BaseMapper<MessageHistoryEntity> {
    /**
     * 根据memoryId查询消息历史列表
     */
    List<MessageHistoryEntity> selectByMemoryId(@Param("memoryId") String memoryId);
    
    /**
     * 根据memoryId查询消息数量
     */
    Integer countByMemoryId(@Param("memoryId") String memoryId);
    
    /**
     * 根据memoryId删除消息历史
     */
    void deleteByMemoryId(@Param("memoryId") String memoryId);
}