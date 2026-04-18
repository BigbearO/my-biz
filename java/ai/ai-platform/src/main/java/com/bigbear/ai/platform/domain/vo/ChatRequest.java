package com.bigbear.ai.platform.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 流式聊天请求
 */
@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    /**
     * 历史消息
     */
    private List<ChatHistoryMessage> history;

    @Data
    public static class ChatHistoryMessage {
        private String role;
        private String content;
    }
}
