package com.bigbear.goofish.core.socket.msg.send;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import com.bigbear.goofish.core.socket.msg.Message;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListUserMessageMsg implements Message {
    private String lwp = "/r/MessageManager/listUserMessages";
    private Headers headers = new Headers();
    private List<Object> body;

    public ListUserMessageMsg(String chatId, int messageAmount) {
        // 使用ArrayList代替List.of以兼容Java 8
        body = new java.util.ArrayList<>();
        body.add(chatId + "@goofish");
        body.add(false);
        body.add(System.currentTimeMillis());
        body.add(messageAmount);
        body.add(false);
    }

    @Data
    public static class Headers {
        private String mid = Message.generateMid();
    }
}
