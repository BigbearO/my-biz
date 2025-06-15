package com.bigbear.order.infrastructure;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import java.util.Objects;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public abstract class GameTaskSender {


    @Resource
    protected RocketMQTemplate rocketMQTemplate;

    public abstract String getQueue();


    public abstract Integer type();

    public void send2Auto(Object data, Object tag) {
        String queue = getQueue();
        send(queue, tag, data);
    }

    //todo header
    // 根据status创建callback-也可以引入消息表机制
    public void send(String queue, Object tag, Object data) {
        Message<String> message2 = MessageBuilder.withPayload(JSONObject.toJSONString(data))
            .build();
        final SendResult sendResult = rocketMQTemplate.syncSend(queue + ":" + tag, message2);
        if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
            // 消息发不出去就抛异常，
            throw new RuntimeException("发送失败");
        }

    }


}
