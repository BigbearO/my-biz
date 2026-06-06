package com.bigbear.ai.ai3d.trigger.api;

import com.bigbear.ai.ai3d.core.agent.PreConsultAgent;
import com.bigbear.ai.ai3d.domain.vo.PreConsultChatRequest;
import com.bigbear.ai.ai3d.domain.vo.SseChatEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
public class PreConsultController {

    private final PreConsultAgent preConsultAgent;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody @Valid PreConsultChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just(ServerSentEvent.builder(
                toJson(SseChatEvent.error("消息不能为空"))).build());
        }

        return preConsultAgent.streamChat(request.getMessage(), request.getChatId())
            .map(event -> ServerSentEvent.<String>builder()
                .data(toJson(event))
                .build())
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("end")
                .data("[DONE]")
                .build()));
    }

    private String toJson(SseChatEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{\"type\":\"error\",\"message\":\"序列化失败\"}";
        }
    }
}
