package com.bigbear.order.triger.task;

import com.bigbear.order.service.AutoGameService;
import com.bigbear.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberTask {

    private final RedisTemplate<String,Object> redisTemplate;

    private final AutoGameService autoGameService;
    /**
     * 扫描当前所有在期限的账号，根据不同的游戏，发送到对应mq进行消费
     * 每个采用redis incr 保证线程安全不重复触发任务
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void sendAutoTask(){

        // 分页查找，也可以根据数据分片来
        autoGameService.task();

    }

}
