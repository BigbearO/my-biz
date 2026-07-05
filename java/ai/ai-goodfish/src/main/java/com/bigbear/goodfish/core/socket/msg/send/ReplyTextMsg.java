package com.bigbear.goodfish.core.socket.msg.send;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import com.bigbear.goodfish.core.socket.msg.Message;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplyTextMsg implements Message {

    private String lwp = "/r/MessageSend/sendByReceiverScope";
    private Headers headers = new Headers();
    private List<Object> body = new ArrayList<>();

    // constructor
    public ReplyTextMsg(String chatId, String receiverId, String senderId, String reply) {

        MessageItem messageItem = new MessageItem();
        // 使用String.format代替formatted方法以兼容Java 8
        messageItem.cid = String.format("%s@goofish", chatId);
        String text = String.format("{\"contentType\":1,\"text\":{\"text\":\"%s\"}}", reply);
        // 直接访问字段，避免使用getter方法
        messageItem.content.getCustom().setData(Base64.getEncoder().encodeToString(text.getBytes()));
        body.add(messageItem);

        ReceiverItem receiverItem = new ReceiverItem();
        // 直接访问字段，避免使用getter方法
        receiverItem.actualReceivers.add(receiverId + "@goofish");
        receiverItem.actualReceivers.add(senderId + "@goofish");
        body.add(receiverItem);
    }

    @Data
    public static class Headers {
        private String mid = Message.generateMid();
    }

    @Data
    public static class MessageItem {
        private String uuid = Message.generateUuid();
        private String cid;
        private int conversationType = 1;
        private Content content = new Content();
        private int redPointPolicy = 0;
        private Extension extension = new Extension();
        private Context ctx = new Context();
        private Object mtags = new Object(); //empty object
        private int msgReadStatusSetting = 1;
    }

    @Data
    public static class Content {
        private int contentType = 101;
        private Custom custom = new Custom();
    }

    @Data
    public static class Custom {
        private int type = 1;
        private String data;
    }

    @Data
    public static class Extension {
        private Object extJson = new Object(); //empty object
    }

    @Data
    public static class Context {
        private String appVersion = "1.0";
        private String platform = "web";
    }

    @Data
    public static class ReceiverItem {
        private List<String> actualReceivers = new ArrayList<>();
    }
}
