package com.xxs.order.triger.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class memberTask {

    /**
     * 扫描当前所有在期限的账号，根据不同的游戏，发送到对应mq进行消费
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void sendAutoTask(){

        LocalDateTime now = LocalDateTime.now();
    }

}
