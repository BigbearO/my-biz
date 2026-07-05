package com.bigbear.goodfish.core.socket.msg.send;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import com.bigbear.goodfish.core.socket.msg.Message;

import java.util.ArrayList;
import java.util.List;

import static com.bigbear.goodfish.utils.JsonUtils.OBJECT_MAPPER;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadMsg implements Message {
    private String lwp = "/r/MessageStatus/read";
    private Headers headers = new Headers();
    private List<Object> body;

    public ReadMsg(String msgId) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        // 使用ArrayList代替List.of()以兼容Java 8
        body = new ArrayList<>();
        body.add(array.add(msgId));
    }

    @Data
    public static class Headers {
        private String mid = Message.generateMid();
    }
}
