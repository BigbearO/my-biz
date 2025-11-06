package com.bigbear.goodfish2.trigger.api;


import com.bigbear.goodfish2.domain.AIService;
import com.bigbear.goodfish2.application.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试验证
 *
 * @author autoagent
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {


    private final AIService aiService;
    private final WebSocketService webSocketService;


    /**
     * 生成自动回复
     */
//    @PostMapping("/reply")
//    public Map<String, Object> generateReply(@RequestBody Map<String, Object> request) {
//        try {
//            String userMessage = (String) request.get("userMessage");
//            String chatId = (String) request.get("chatId");
//
//            if (userMessage == null || chatId == null) {
//                return Map.of("success", false, "message", "缺少必要参数");
//            }
//
//            // 检查是否处于人工模式
//            if (webSocketService.isManualMode(chatId)) {
//                return Map.of(
//                    "success", true,
//                    "manualMode", true,
//                    "message", "当前处于人工模式，不生成自动回复"
//                );
//            }
//
//            // 获取聊天历史
//            List<Message> historyMessages = chatService.getChatHistory(chatId, 50);
//            List<Map<String, String>> chatHistory = historyMessages.stream()
//                .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
//                .collect(Collectors.toList());
//
//            // 检测用户意图
//            String intent = aiService.detectIntent(userMessage, itemDescription,
//                aiService.formatHistory(chatHistory));
//
//            // 获取议价次数
//            int bargainCount = 0;
//            if ("price".equals(intent)) {
//                bargainCount = chatService.getBargainCount(chatId);
//            }
//
//            // 生成回复
//            String reply = aiService.generateReply(intent, userMessage, itemDescription,
//                chatHistory, bargainCount);
//
//            // 如果是议价意图，增加议价次数
//            if ("price".equals(intent)) {
//                chatService.incrementBargainCount(chatId);
//            }
//
//            return Map.of(
//                "success", true,
//                "message", "生成回复成功",
//                "data", Map.of(
//                    "reply", reply,
//                    "intent", intent
//                )
//            );
//        } catch (Exception e) {
//            log.error("生成回复失败", e);
//            return Map.of("success", false, "message", "生成回复失败: " + e.getMessage());
//        }
//    }

}
