package com.xianyu.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyu.agent.model.ChatBargainCount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ChatBargainCountMapper extends BaseMapper<ChatBargainCount> {
    @Select("SELECT count FROM chat_bargain_counts WHERE chat_id = #{chatId}")
    Integer findCountByChatId(@Param("chatId") String chatId);
}
