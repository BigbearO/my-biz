package com.bigbear.order.triger.consumer;

import com.xxs.order.api.common.constants.Games;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "games", selectorExpression= Games.CHEN_BAI_JIN_QU,consumerGroup = "auto-games")
public class TestConsumer implements RocketMQListener<String> {
    public void onMessage(String message) {
        log.info("received message: {}", message);
    }
}


