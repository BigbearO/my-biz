package com.bigbear.ai.biz.infra.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.ai.biz.domain.entity.ChatMemoryEntity;
import com.bigbear.ai.biz.infra.mapper.ChatMemoryMapper;

import org.springframework.stereotype.Service;

@Service
public class ChatMemoryManager extends ServiceImpl<ChatMemoryMapper, ChatMemoryEntity> {

    public ChatMemoryEntity selectByMemoryId(String memoryId) {
        LambdaQueryWrapper<ChatMemoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMemoryEntity::getMemoryId, memoryId).last(" limit 1");
        return this.getOne(queryWrapper);
    }
}
