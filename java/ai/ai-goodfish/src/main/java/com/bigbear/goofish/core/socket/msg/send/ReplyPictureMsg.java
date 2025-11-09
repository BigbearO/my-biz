package com.bigbear.goofish.core.socket.msg.send;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import com.bigbear.goofish.core.socket.msg.Message;
import com.bigbear.goofish.utils.JsonUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplyPictureMsg implements Message {

    private String lwp = "/r/MessageSend/sendByReceiverScope";
    private Headers headers = new Headers();
    private List<Object> body = new ArrayList<>();
    private static final Pattern pattern = Pattern.compile("(?<width>\\d+)\\*(?<height>\\d+)");
    
    // 类级别的静态内部类定义
    @Data
    public static class MessageContent {
        private int contentType;
        private ImageData image;
        
        // 显式定义构造器
        public MessageContent(int contentType, ImageData image) {
            this.contentType = contentType;
            this.image = image;
        }
        
        @Data
        public static class ImageData {
            private List<PicInfo> pics;
            
            // 显式定义构造器
            public ImageData(List<PicInfo> pics) {
                this.pics = pics;
            }
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class PicInfo {
        private int height;
        private int type;
        private String url;
        private int width;
    }

    // constructor
    public ReplyPictureMsg(String chatId, String receiverId, String senderId, JsonNode picAddress) {
        MessageItem messageItem = new MessageItem();
        // 使用String.format代替formatted方法以兼容Java 8
        messageItem.cid = String.format("%s@goofish", chatId);

        List<PicInfo> pics = new ArrayList<>();
        
        // width * height
        String pix = picAddress.path("pix").asText();
        Matcher matcher = pattern.matcher(pix);

        if (matcher.find()) {
            String width = matcher.group("width");
            String height = matcher.group("height");
            pics.add(new PicInfo(Integer.parseInt(height), 0,
                    picAddress.path("url").asText(),
                    Integer.parseInt(width)));

            MessageContent.ImageData imageData = new MessageContent.ImageData(pics);
            MessageContent content = new MessageContent(2, imageData);

            // 直接访问content字段，避免使用getter方法
            messageItem.content.getCustom().setData(Base64.getEncoder().encodeToString(JsonUtils.toJson(content).getBytes()));
            body.add(messageItem);

            ReceiverItem receiverItem = new ReceiverItem();
            // 直接访问actualReceivers字段，避免使用getter方法
            receiverItem.actualReceivers.add(receiverId + "@goofish");
            receiverItem.actualReceivers.add(senderId + "@goofish");
            body.add(receiverItem);
        }
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
        private int type = 2;
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
