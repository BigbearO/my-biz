package com.bigbear.ai.biz.infra.manager;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.ai.biz.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.biz.infra.mapper.MessageHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageHistoryManager extends ServiceImpl<MessageHistoryMapper, MessageHistoryEntity> {


}
