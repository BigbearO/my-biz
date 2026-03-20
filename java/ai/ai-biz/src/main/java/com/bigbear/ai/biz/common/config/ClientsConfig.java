package com.bigbear.ai.biz.common.config;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;

import cn.hutool.extra.spring.SpringUtil;
import com.bigbear.ai.biz.common.util.MyChatModelListener;
import com.bigbear.ai.biz.infra.manager.PersistentChatMemoryStore;
import com.bigbear.ai.biz.infra.manager.DbChatMemoryStore;
import com.bigbear.ai.biz.service.ChatAssistant;
import com.bigbear.ai.biz.service.PetDoctor;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ValueOperations;


@AutoConfiguration
@RequiredArgsConstructor
public class ClientsConfig {

    /**
     * 自定义的
     *
     * @return
     */
    @Bean
    public OpenAiChatModel mediaContentRecognitionModel() {
        return OpenAiChatModel.builder()
                .apiKey(SpringUtil.getProperty("llm.ak"))
                .modelName("qwen-vl-max")  // 设置使用的模型名称
                .httpClientBuilder(JdkHttpClient.builder())
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
    }

    @Bean
    @Primary
    public OpenAiChatModel myChatModel() {

        return OpenAiChatModel.builder()
                .timeout(Duration.ofSeconds(10)) //10s超时
                .logRequests(true)    // 启用请求日志
                .logResponses(true)
                // todo 想想之后咋解决，不通过指定的方式
                .httpClientBuilder(JdkHttpClient.builder())

//				.listeners(List.of(chatModelListener()))
//				.maxRetries(5)  // 设置重试策略
                .apiKey(SpringUtil.getProperty("llm.chat.ak"))
                .maxTokens(1024)
                .timeout(Duration.ofSeconds(30))
//                .modelName("DeepSeek-R1-Distill-Qwen-14B")
                .modelName("Qwen/Qwen3.5-4B")
                .modelName(SpringUtil.getProperty("llm.chat.model"))
                .baseUrl(SpringUtil.getProperty("llm.chat.url"))
                .build();
    }


    @Bean
    @Primary
    public StreamingChatModel myStreamingModel() {

        return OpenAiStreamingChatModel.builder()
                .timeout(Duration.ofSeconds(10)) //10s超时
                .logRequests(true)    // 启用请求日志
                .logResponses(true)
                // todo 想想之后咋解决，不通过指定的方式
                .httpClientBuilder(JdkHttpClient.builder())

//				.listeners(List.of(chatModelListener()))
//				.maxRetries(5)  // 设置重试策略
                .apiKey(SpringUtil.getProperty("llm.ak"))
                .maxTokens(1024)
                .timeout(Duration.ofSeconds(30))
                .modelName("DeepSeek-R1-Distill-Qwen-14B")
                .baseUrl("https://ai.gitee.com/v1")
                .build();
    }

    @Bean
    public ChatModel qwenVLModel() {
        return QwenChatModel.builder()
                .apiKey("You API key here")
                .modelName("qwen-vl-max")
                .build();
    }

    @Bean
    public ChatModelListener chatModelListener() {
        return new MyChatModelListener();
    }

    @Bean
    // 这个bean也可以使用 @AiService标记接口ChatAssistant来实现
    public ChatAssistant chatAssistant(@Qualifier("myChatModel") ChatModel ChatModel) {

        return AiServices.builder(ChatAssistant.class)
                .chatModel(ChatModel)
                .chatMemoryProvider(chatMemoryProvider())
                .build();
    }

    @Bean
    public PetDoctor petDoctor(@Qualifier("myChatModel") ChatModel ChatModel) {
        return AiServices.builder(PetDoctor.class)
                .chatModel(ChatModel)
                .chatMemoryProvider(chatMemoryProvider())
                .build();
    }

    @Bean
    public ChatMemoryProvider pgChatMemory(DbChatMemoryStore pgChatMemoryStore) {
        return memoryId ->  MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(10)
            .chatMemoryStore(pgChatMemoryStore)
            .build();
    }


    // MessageWindowChatMemory 保留最近的几条问题，不管token大小
    @Bean
    @Primary
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                // 设置消息窗口 ID
                .id(memoryId)
                // 设置消息最大条数，默认为 10
                .maxMessages(10)
                .chatMemoryStore(SpringUtil.getBean(PersistentChatMemoryStore.class))
//
                .build();
    }


    @Bean
    public PersistentChatMemoryStore chatMemoryStore(ValueOperations<String, String> valueOperations) {
        return new PersistentChatMemoryStore(valueOperations);
    }

    // -------------下面是带token限制的 ,就是去掉超过你的限制数token的问题
    @Bean
    public ChatMemoryProvider tokenChatMemoryProvider() {

        TokenCountEstimator countEstimator=new OpenAiTokenCountEstimator(GPT_3_5_TURBO);
        return memoryId -> TokenWindowChatMemory.builder()
                // 设置消息窗口 ID
                .id(memoryId)
                .maxTokens(1000, countEstimator)
                // 设置消息最大条数，默认为 10
                .chatMemoryStore(SpringUtil.getBean(ChatMemoryStore.class))
                .build();
    }




}
