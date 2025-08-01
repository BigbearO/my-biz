package com.bigbear.order.biz.trigger.task;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;


@ApplicationScoped
@RequiredArgsConstructor
public class MemberTask {

    /**
     * 扫描当前所有在期限的账号，根据不同的游戏，发送到对应mq进行消费
     * 每个采用redis incr 保证线程安全不重复触发任务
     */
    @Scheduled(cron = "0 1/30 * * * ? ")
    @Scheduled(cron="0 15 10 * * ?")
    public void sendAutoTask(){

        // 分页查找，也可以根据数据分片来
//        autoGameService.task();
        System.out.println("sendAutoTask");
    }
}
