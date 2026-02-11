package com.bigbear.mall.biz.common;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Objects;

/**
 * 游戏任务发送器抽象类
 * 不同游戏类型继承此类实现具体的任务发送逻辑
 */
public abstract class GameTaskSender {

    @Resource
    protected RocketMQTemplate rocketMQTemplate;

    /**
     * 获取消息队列名称
     */
    public abstract String getQueue();

    /**
     * 获取游戏类型
     */
    public abstract Integer type();

    /**
     * 发送自动任务消息
     */
    public void send2Auto(Object data, Object tag) {
        String queue = getQueue();
        send(queue, tag, data);
    }

    /**
     * 发送消息到指定队列
     * TODO: 根据status创建callback-也可以引入消息表机制
     */
    public void send(String queue, Object tag, Object data) {
        Message<String> message = MessageBuilder.withPayload(JSONObject.toJSONString(data))
                .build();
        SendResult sendResult = rocketMQTemplate.syncSend(queue + ":" + tag, message);
        if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
            throw new RuntimeException("MQ消息发送失败: " + queue);
        }
    }
}
