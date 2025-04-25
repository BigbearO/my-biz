package com.bigbear.order.infrastructure;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public abstract class GameTaskSender {
    //todo 自定义注解解决注入问题
    @Resource
    protected RocketMQTemplate rocketMQTemplate;

    public void send2Auto(Object data,Object tag) {
        String queue = getQueue();
        send(queue,tag,data);
    }

    public abstract String getQueue();

    public   void send(String queue,Object tag,Object data) {
        //todo 使用其他json
        rocketMQTemplate.convertAndSend(queue+":"+tag, JSONObject.toJSONString(data));
    }

    public abstract Integer type();

}
