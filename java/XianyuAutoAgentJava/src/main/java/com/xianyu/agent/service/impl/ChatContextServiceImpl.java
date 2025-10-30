package com.xianyu.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xianyu.agent.mapper.ChatBargainCountMapper;
import com.xianyu.agent.mapper.ItemMapper;
import com.xianyu.agent.mapper.MessageMapper;
import com.xianyu.agent.model.ChatBargainCount;
import com.xianyu.agent.model.Item;
import com.xianyu.agent.model.Message;
import com.xianyu.agent.service.ChatContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatContextServiceImpl implements ChatContextService {

    @Resource
    private MessageMapper messageMapper;
    @Resource
    private ItemMapper itemMapper;
    @Resource
    private ChatBargainCountMapper chatBargainCountMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void saveItemInfo(String itemId, Map<String, Object> itemData) {
        Item item = new Item();
        item.setItemId(itemId);
        try {
            item.setData(objectMapper.writeValueAsString(itemData));
        } catch (Exception e) {
            item.setData("{}");
        }
        item.setPrice(itemData.get("price") != null ? itemData.get("price").toString() : "");
        item.setDescription(itemData.get("title") != null ? itemData.get("title").toString() : "");
        item.setLastUpdated(LocalDateTime.now());

        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("item_id", itemId);
        if (itemMapper.exists(wrapper)) {
            itemMapper.update(item, wrapper);
        } else {
            itemMapper.insert(item);
        }
    }

    @Override
    public Map<String, Object> getItemInfo(String itemId) {
        QueryWrapper<Item> wrapper = new QueryWrapper<>();
        wrapper.eq("item_id", itemId);
        Item item = itemMapper.selectOne(wrapper);
        if (item != null) {
            try {
                return objectMapper.readValue(item.getData(), Map.class);
            } catch (Exception e) {
                // ignore
            }
        }
        return new HashMap<>();
    }

    @Override
    public void addMessageByChat(String chatId, String userId, String itemId, String role, String content) {
        Message message = new Message();
        message.setChatId(chatId);
        message.setUserId(userId);
        message.setItemId(itemId);
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        messageMapper.insert(message);
    }

    @Override
    public List<Map<String, String>> getContextByChat(String chatId) {
        List<Message> messages = messageMapper.findByChatId(chatId, 20);
        List<Map<String, String>> result = new ArrayList<>();
        for (Message message : messages) {
            Map<String, String> msgMap = new HashMap<>();
            msgMap.put("role", message.getRole());
            msgMap.put("content", message.getContent());
            result.add(msgMap);
        }
        return result;
    }

    @Override
    public void incrementBargainCountByChat(String chatId) {
        ChatBargainCount count = new ChatBargainCount();
        count.setChatId(chatId);
        count.setCount(1);
        count.setLastUpdated(LocalDateTime.now());

        QueryWrapper<ChatBargainCount> wrapper = new QueryWrapper<>();
        wrapper.eq("chat_id", chatId);
        if (chatBargainCountMapper.exists(wrapper)) {
            UpdateWrapper<ChatBargainCount> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("chat_id", chatId)
                    .setSql("count = count + 1, last_updated = #{lastUpdated}");
            chatBargainCountMapper.update(count, updateWrapper);
        } else {
            chatBargainCountMapper.insert(count);
        }
    }

    @Override
    public int getBargainCountByChat(String chatId) {
        Integer count = chatBargainCountMapper.findCountByChatId(chatId);
        return count != null ? count : 0;
    }
}
