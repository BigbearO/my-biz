package com.bigbear.goofish.task;

import com.bigbear.goofish.app.UserWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
//@EnableScheduling
@Component
@RequiredArgsConstructor
public class GoodFishTokenTask {

    private final UserWebSocketService userWebSocketService;


    @Scheduled(cron = "0 */30 * * * *")
    public void refreshToken() {
        log.info(" start refresh good fish Token");
        userWebSocketService.refreshToken();
    }

}
