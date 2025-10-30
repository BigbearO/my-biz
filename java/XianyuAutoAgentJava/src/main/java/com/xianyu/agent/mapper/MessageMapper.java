package com.xianyu.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyu.agent.model.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {
    @Select("SELECT role, content FROM messages WHERE chat_id = #{chatId} ORDER BY timestamp ASC LIMIT #{limit}")
    List<Message> findByChatId(@Param("chatId") String chatId, @Param("limit") int limit);
}
