package com.bigbear.order.infrastructure;

import org.apache.rocketmq.spring.core.RocketMQTemplate;

import javax.annotation.Resource;

public abstract class GameTaskSender {
    //todo 自定义注解解决注入问题
    @Resource
    protected RocketMQTemplate rocketMQTemplate;

    public void send2Auto(Object data) {
        String queue = getQueue();
        send(queue,data);
    }

    public abstract String getQueue();

    public abstract void send(String queue,Object data);

    public abstract Integer type();

}
