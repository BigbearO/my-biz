package com.bigbear.ai.ai3d.domain.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PreConsultChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    @NotBlank(message = "会话id不能为空")
    private String chatId;
}
