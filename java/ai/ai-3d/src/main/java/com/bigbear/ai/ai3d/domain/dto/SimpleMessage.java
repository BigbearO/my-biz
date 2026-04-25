package com.bigbear.ai.ai3d.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 14399L;

    private String role;
    private String content;

    public SimpleMessage() {}

    public SimpleMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
