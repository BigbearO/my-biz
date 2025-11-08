package com.bigbear.ai.biz.service;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.ai.biz.domain.entity.ItemInfo;
import com.bigbear.ai.biz.manager.AiServiceContainer;
import com.bigbear.ai.biz.manager.DbChatMemoryStore;
import com.bigbear.ai.biz.manager.ItemManager;
import com.bigbear.ai.core.IAiService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * 基于RAG的商品对话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemChatService implements InitializingBean {

    private final OpenAiChatModel myChatModel;

    private final DbChatMemoryStore chatMemoryStore;

    private final ItemManager itemManager;
    // 系统级别的消息只会保留一个
    public SystemMessage itemRolePrompt;

    public UserMessage emotionPrompt;

    String sysPrompt;
    String userPrompt;

    @Value("${goodfish.system-prompt}")
    Resource systemPromptResource;

    @Value("${goodfish.emotion-prompt}")
    Resource emotionPromptResource;

    // 内存中的向量存储，用于测试
    private final ConcurrentHashMap<String, EmbeddingStore> embeddingStoreCache = new ConcurrentHashMap<>();


    @Override
    public void afterPropertiesSet() throws Exception {
        sysPrompt = new String(
            FileCopyUtils.copyToByteArray(systemPromptResource.getInputStream()));

        itemRolePrompt = SystemMessage.from(sysPrompt);
        userPrompt = new String(
            FileCopyUtils.copyToByteArray(emotionPromptResource.getInputStream()));

        emotionPrompt = UserMessage.from(userPrompt);


    }

    /**
     * 商品对话接口
     *
     * @param tenantId       租户ID
     * @param itemId         商品ID
     * @param conversationId 对话ID
     * @param userQuery      用户问题
     * @return AI回复
     */

    @Deprecated
    public String chat(String tenantId, Long itemId, String conversationId, String userQuery) {
        try {
            // 生成memoryId
            String memoryId = tenantId + "_" + itemId + "_" + conversationId;

            // 获取商品信息
            ItemEntity itemInfo = itemManager.getById(itemId);
            if (itemInfo == null) {
                return "未找到指定商品信息";
            }

            // 构建对话历史
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryStore(chatMemoryStore)
                .id(memoryId)
                .build();

            // 获取对话历史
            List<ChatMessage> messages = new ArrayList<>();
            //todo 这个是否会持久化
            messages.add(itemRolePrompt);
            messages.add(emotionPrompt);

            messages.addAll(chatMemory.messages());
            //todo rag
            // 构建RAG增强的提示词
//            String enhancedPrompt = buildEnhancedPrompt(itemInfo, userQuery);
//            // 添加用户消息
//            messages.add(UserMessage.from(enhancedPrompt));

            // 调用LLM进行回复
            ChatResponse response = myChatModel.chat(messages);
            //todo 异步的 保持消耗的token
            response.tokenUsage();

            String aiResponse = response.aiMessage().text();

            // 保存对话历史
            chatMemory.add(response.aiMessage());

            log.info("商品对话完成: tenantId={}, itemId={}, conversationId={}",
                tenantId, itemId, conversationId);

            return aiResponse;

        } catch (Exception e) {
            log.error("商品对话失败: {}", e.getMessage(), e);
            return "对话处理失败，请稍后重试";
        }
    }

    // todo
    //  1 将商品信息组织上下文  2 加上Rag
    public String chat(Long itemId, String conversationId, String question) {
        try {
            // 生成memoryId
            String memoryId;

            if (StringUtils.hasLength(conversationId)) {
                memoryId = conversationId;
            } else {
                memoryId = itemId + "_" + conversationId;
            }

            // 获取商品信息
            ItemEntity itemInfo = itemManager.getById(itemId);
            if (itemInfo == null) {
                log.info("未找到指定商品信息:id={}", itemId);
                return "";
            }

            AiServiceContainer serviceContainer = SpringUtil.getBean(AiServiceContainer.class);

            IAiService aiService = serviceContainer.get(itemId.toString(), IAiService.class);

            String formatted = """
                基于以下要求和商品信息生成你的回复
                要求： %s
                商品信息: %s
                """.formatted(userPrompt,JSONUtil.toJsonStr(itemInfo));
            // 调用LLM进行回复
            String aiResponse = aiService.chatWithSystem(memoryId, sysPrompt, formatted, question);

            log.info("商品对话完成:, itemId={}, conversationId={}",
                itemId, conversationId);

            return aiResponse;

        } catch (Exception e) {
            log.error("商品对话失败: {}", e.getMessage(), e);
            return "对话处理失败，请稍后重试";
        }
    }

    /**
     * 构建增强的提示词
     */
    private String buildEnhancedPrompt(ItemInfo itemInfo, String userQuery) {
        StringBuilder promptBuilder = new StringBuilder();

        // 添加系统提示
        promptBuilder.append("你是一个专业的商品顾问，请基于以下商品信息回答用户问题：\n\n");

        // 添加商品信息
        promptBuilder.append("【商品信息】\n");
        promptBuilder.append("商品描述: ").append(itemInfo.getItemDO().getDesc()).append("\n");
        promptBuilder.append("售价: ").append(itemInfo.getItemDO().getSoldPrice()).append("\n");

        // 添加定价策略提示词
        if (itemInfo.getItemDO().getPricingStrategyPrompt() != null) {
            promptBuilder.append("定价策略: ")
                .append(itemInfo.getItemDO().getPricingStrategyPrompt()).append("\n");
        }

        // 添加标签信息
        if (itemInfo.getItemDO().getItemLabelExtList() != null) {
            promptBuilder.append("商品标签: ");
            for (ItemInfo.ItemLabelExt labelExt : itemInfo.getItemDO().getItemLabelExtList()) {
                promptBuilder.append(labelExt.getProperties()).append(", ");
            }
            promptBuilder.append("\n");
        }

        // 添加卖家信息
        if (itemInfo.getSellerDO() != null) {
            promptBuilder.append("卖家城市: ").append(itemInfo.getSellerDO().getCity())
                .append("\n");
            promptBuilder.append("卖家签名: ").append(itemInfo.getSellerDO().getSignature())
                .append("\n");
        }

        // 添加用户问题
        promptBuilder.append("\n【用户问题】\n").append(userQuery);

        return promptBuilder.toString();
    }


}
