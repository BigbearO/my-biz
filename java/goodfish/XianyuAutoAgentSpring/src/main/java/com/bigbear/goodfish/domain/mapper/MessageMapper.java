package com.bigbear.goodfish.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bigbear.goodfish.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息Mapper
 *
 * @author autoagent
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 根据用户ID和商品ID查询消息列表
     */
    List<Message> findByUserIdAndItemId(@Param("userId") String userId, @Param("itemId") String itemId);

    /**
     * 根据聊天ID查询消息列表
     */
    List<Message> findByChatId(@Param("chatId") String chatId, @Param("limit") int limit);

    /**
     * 查询最近N条消息
     */
    List<Message> findRecentMessages(@Param("limit") int limit);

    /**
     * 删除过期消息
     */
    int deleteExpiredMessages(@Param("expireTime") LocalDateTime expireTime);
}
