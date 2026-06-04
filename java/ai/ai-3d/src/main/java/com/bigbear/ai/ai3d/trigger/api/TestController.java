package com.bigbear.ai.ai3d.trigger.api;

import com.bigbear.ai.ai3d.domain.entity.MessageHistoryEntity;
import com.bigbear.ai.ai3d.infra.manager.MessageHistoryManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final MessageHistoryManager messageHistoryManager;

    @GetMapping("/messages")
    public Mono<List<MessageHistoryEntity>> getMessagesByChatId(@RequestParam String chatId) {
        List<MessageHistoryEntity> list = messageHistoryManager.findByChatId(chatId);
        log.info("查询 chatId={} 的消息，共 {} 条", chatId, list.size());
        return Mono.just(list);
    }
}
