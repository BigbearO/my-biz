package com.bigbear.ai.ai3d.infra.manager;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.bigbear.ai.ai3d.domain.entity.PreConsultSummaryEntity;
import com.bigbear.ai.ai3d.infra.mapper.PreConsultSummaryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PreConsultSummaryManager extends ServiceImpl<PreConsultSummaryMapper, PreConsultSummaryEntity> {

    public PreConsultSummaryEntity findByChatId(String chatId) {
        QueryWrapper wrapper = QueryWrapper.create()
            .from(PreConsultSummaryEntity.class)
            .where(PreConsultSummaryEntity::getChatId).eq(chatId)
            .limit(1);
        return mapper.selectOneByQuery(wrapper);
    }

    public void saveSummary(PreConsultSummaryEntity entity) {
        PreConsultSummaryEntity existing = findByChatId(entity.getChatId());
        if (existing != null) {
            entity.setId(existing.getId());
            mapper.update(entity);
        } else {
            mapper.insert(entity);
        }
    }
}
