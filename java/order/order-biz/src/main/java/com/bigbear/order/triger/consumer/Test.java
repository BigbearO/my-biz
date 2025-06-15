//package com.bigbear.order.triger.consumer;
//
//import java.util.List;
//import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
//import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
//import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
//import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
//import org.apache.rocketmq.common.message.MessageExt;
//import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
//import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
//import org.springframework.stereotype.Service;
// todo 手动ack示例 但是也不用，默认消费失败会重试，直到达到最大次数，可以自己在消费端处理
//@Service
//@RocketMQMessageListener(
//    topic = "test_topic",
//    consumerGroup = "my-consumer-group"
//)
//public class Test implements RocketMQPushConsumerLifecycleListener {
//
//    @Override
//    public void prepareStart(DefaultMQPushConsumer consumer) {
//        consumer.registerMessageListener(new MessageListenerConcurrently() {
//            @Override
//            public ConsumeConcurrentlyStatus consumeMessage(
//                List<MessageExt> messages,
//                ConsumeConcurrentlyContext context
//            ) {
//                try {
//                    for (MessageExt msg : messages) {
//                        System.out.println("收到消息: " + new String(msg.getBody()));
//                        // 业务处理...
//                    }
//                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS; // 成功
//                } catch (Exception e) {
//                    return ConsumeConcurrentlyStatus.RECONSUME_LATER; // 失败后重试
//                }
//            }
//        });
//    }
//
//
//
//}
