package com.bigbear.ai.ai3d.infra.manager;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.ai.ai3d.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.ai3d.infra.mapper.MessageHistoryMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageHistoryManager extends ServiceImpl<MessageHistoryMapper, MessageHistoryEntity> {

    public List<MessageHistoryEntity> findByChatId(String chatId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(MessageHistoryEntity.class)
                .where(MessageHistoryEntity::getChatId).eq(chatId)
                .orderBy(MessageHistoryEntity::getCreateTime, true);
        return mapper.selectListByQuery(wrapper);
    }
}
