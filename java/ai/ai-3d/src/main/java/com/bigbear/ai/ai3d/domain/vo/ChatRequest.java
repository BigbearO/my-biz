package com.bigbear.ai.ai3d.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;
    @NotBlank(message = "会话id不能为空")
    private String conversationId;

    private List<ChatHistoryMessage> history;

    @Data
    public static class ChatHistoryMessage {
        private String role;
        private String content;
    }
}
