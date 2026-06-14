//package com.bigbear.ai.ai3d.core.hook;
//
//import com.alibaba.cloud.ai.graph.OverAllState;
//import com.alibaba.cloud.ai.graph.RunnableConfig;
//import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
//import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
//import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
//import jakarta.annotation.Resource;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.messages.Message;
//import org.springframework.ai.chat.messages.UserMessage;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//
//@Component
//@HookPositions({HookPosition.AFTER_MODEL}) // LLM调用完成后执行
//public class MessageSaveModelHook extends ModelHook {
//
//    @Resource
//    private ChatHistoryService chatHistoryService;
//
//    @Override
//    public String getName() {
//        return "message-save-model-hook";
//    }
//
//    @Override
//    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
//        // 1. 取出全部消息列表
//        List<Message> messages = state.value("messages")
//            .map(val -> (List<Message>) val)
//            .orElse(List.of());
//        if (messages.isEmpty()) {
//            return CompletableFuture.completedFuture(Map.of());
//        }
//
//        // 2. 会话标识
//        String threadId = config.threadId().get();
//        String sessionId = (String) config.context().getOrDefault("sessionId", threadId);
//
//        // 3. 遍历每一条Message，逐条入库
//        for (int idx = 0; idx < messages.size(); idx++) {
//            Message msg = messages.get(idx);
//            AiChatHistory history = new AiChatHistory();
//            history.setSessionId(sessionId);
//            history.setThreadId(threadId);
//            history.setMsgIndex(idx); // 同会话内有序序号，用于唯一防重
//
//            if (msg instanceof UserMessage) {
//                history.setMsgType("user");
//                history.setContent(msg.getText());
//            } else if (msg instanceof AssistantMessage) {
//                history.setMsgType("assistant");
//                history.setContent(msg.getText());
//            } else {
//                // 系统消息、工具消息等不存入业务历史表，可按需放开
//                continue;
//            }
//
//            // 插入，冲突自动跳过
//            chatHistoryService.insertIgnore(history);
//        }
//
//        return CompletableFuture.completedFuture(Map.of());
//    }
//}
