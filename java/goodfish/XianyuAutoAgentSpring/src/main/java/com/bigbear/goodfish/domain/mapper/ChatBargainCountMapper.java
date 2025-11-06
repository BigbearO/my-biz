package com.bigbear.goodfish.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.goodfish.domain.entity.ChatBargainCount;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天议价次数Mapper
 *
 * @author autoagent
 */
public interface ChatBargainCountMapper extends BaseMapper<ChatBargainCount> {

    /**
     * 根据聊天ID查询议价次数
     */
    ChatBargainCount selectByChatId(@Param("chatId") String chatId);

    /**
     * 增加议价次数
     */
    int incrementCount(@Param("chatId") String chatId);
}
