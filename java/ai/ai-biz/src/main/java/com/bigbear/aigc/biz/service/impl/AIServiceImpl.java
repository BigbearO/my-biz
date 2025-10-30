package com.bigbear.aigc.biz.service.impl;


import com.bigbear.goodfish.domain.AIService;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * AI服务实现
 *
 * @author autoagent
 */
@Slf4j
@Service
public class AIServiceImpl implements AIService {

    private final ChatModel chatModel;
    private final ResourceLoader resourceLoader;

    private String classifyPrompt;
    private String pricePrompt;
    private String techPrompt;
    private String defaultPrompt;

    public AIServiceImpl(ChatModel chatModel, ResourceLoader resourceLoader) {
        this.chatModel = chatModel;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initPrompts() {
        try {
            // 从resources/prompts目录加载提示词
            classifyPrompt = loadPrompt("prompts/classify_prompt.txt");
//            pricePrompt = loadPrompt("prompts/price_prompt.txt");
//            techPrompt = loadPrompt("prompts/tech_prompt.txt");
            defaultPrompt = loadPrompt("prompts/default_prompt.txt");

            log.info("成功加载所有提示词");
        } catch (IOException e) {
            log.error("加载提示词失败", e);
            throw new RuntimeException("加载提示词失败", e);
        }
    }

    private String loadPrompt(String path) throws IOException {
        try {
            Path promptPath = Paths.get("prompts", path);
            if (Files.exists(promptPath)) {
                return Files.readString(promptPath);
            }
            // 尝试从resources目录加载
            return new String(resourceLoader.getResource("classpath:" + path).getInputStream().readAllBytes());
        } catch (Exception e) {
            // 如果文件不存在，返回空的默认提示词
            log.warn("提示词文件不存在: {}", path);
            return "你是一个AI助手，请根据用户的问题给出合适的回答。";
        }
    }

    @Override
    public String detectIntent(String userMessage, String itemDescription, String chatHistory) {
        String prompt = String.format("%s\n\n用户消息: %s\n\n商品描述: %s\n\n对话历史: %s\n\n请识别用户的意图:",
                classifyPrompt, userMessage, itemDescription, chatHistory);

        String  intent = chatModel.chat(prompt);


        // 简单的意图提取逻辑，实际项目中可能需要更复杂的处理
        if (intent.contains("价格") || intent.contains("议价")) {
            return "price";
        } else if (intent.contains("技术") || intent.contains("规格")) {
            return "tech";
        } else {
            return "default";
        }
    }

    @Override
    public String generateReply(String intent, String userMessage, String itemDescription, List<Map<String, String>> chatHistory, int bargainCount) {
        String formattedHistory = formatHistory(chatHistory);
        String prompt = switch (intent) {
            case "price" -> String.format(
                "%s\n\n用户消息: %s\n\n商品描述: %s\n\n对话历史: %s\n\n议价次数: %d\n\n请生成回复:",
                pricePrompt, userMessage, itemDescription, formattedHistory, bargainCount);
            case "tech" ->
                String.format("%s\n\n用户消息: %s\n\n商品描述: %s\n\n对话历史: %s\n\n请生成回复:",
                    techPrompt, userMessage, itemDescription, formattedHistory);
            default ->
                String.format("%s\n\n用户消息: %s\n\n商品描述: %s\n\n对话历史: %s\n\n请生成回复:",
                    defaultPrompt, userMessage, itemDescription, formattedHistory);
        };

        String   reply = chatModel.chat(prompt);
        // 应用安全过滤
        return safeFilter(reply);
    }

    @Override
    public String safeFilter(String text) {
        // 简单的安全过滤实现
        String[] blockedPhrases = {"微信", "QQ", "支付宝", "银行卡", "线下"};
        for (String phrase : blockedPhrases) {
            if (text.contains(phrase)) {
                return "[安全提醒]请通过平台沟通";
            }
        }
        return text;
    }

    @Override
    public String formatHistory(List<Map<String, String>> context) {
        StringBuilder sb = new StringBuilder();
        if (context != null) {
            for (Map<String, String> msg : context) {
                if (msg.containsKey("role") && msg.containsKey("content")) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    sb.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
