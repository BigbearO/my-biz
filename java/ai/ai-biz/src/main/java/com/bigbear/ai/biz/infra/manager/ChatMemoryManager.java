package com.bigbear.ai.biz.infra.manager;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.ai.biz.domain.entity.ChatMemoryEntity;
import com.bigbear.ai.biz.infra.mapper.ChatMemoryMapper;

import org.springframework.stereotype.Service;

@Service
public class ChatMemoryManager extends ServiceImpl<ChatMemoryMapper, ChatMemoryEntity> {

    public ChatMemoryEntity selectByMemoryId(String memoryId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .from(ChatMemoryEntity.class)
            .where(ChatMemoryEntity::getMemoryId).eq(memoryId)
            .limit(1);
        return mapper.selectOneByQuery(queryWrapper);
    }
}
