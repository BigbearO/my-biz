package com.bigbear.aigc.biz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bigbear.aigc.biz.domain.entity.ChatMemoryEntity;
import com.bigbear.aigc.biz.domain.entity.MessageHistoryEntity;
import com.bigbear.aigc.biz.mapper.ChatMemoryMapper;
import com.github.yulichang.base.MPJBaseServiceImpl;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatMemoryManager extends MPJBaseServiceImpl<ChatMemoryMapper, ChatMemoryEntity> {

    public ChatMemoryEntity selectByMemoryId(String memoryId) {
        LambdaQueryWrapper<ChatMemoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMemoryEntity::getMemoryId, memoryId).last(" limit 1");
        return this.getOne(queryWrapper);


    }
}
