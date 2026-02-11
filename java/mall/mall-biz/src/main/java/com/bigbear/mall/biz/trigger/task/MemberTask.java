package com.bigbear.mall.biz.trigger.task;

import com.bigbear.mall.biz.application.AutoGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 会员定时任务
 * 扫描当前所有在期限的账号，发送到对应游戏队列进行自动化处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberTask {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AutoGameService autoGameService;

    /**
     * 每10分钟执行一次
     * 扫描当前所有在期限的账号，根据不同的游戏发送到对应MQ进行消费
     * 每个采用redis incr保证线程安全不重复触发任务
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void sendAutoTask() {
        log.info("开始执行会员定时任务，时间: {}", LocalDateTime.now());
        autoGameService.task();
        log.info("会员定时任务执行完成");
    }
}
