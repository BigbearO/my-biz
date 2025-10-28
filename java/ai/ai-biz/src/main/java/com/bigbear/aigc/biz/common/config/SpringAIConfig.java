package com.bigbear.aigc.biz.common.config;

import static org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil.resolveConnectionProperties;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(after = {RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
    SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class},
    before = {org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class})
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({OpenAiConnectionProperties.class, OpenAiChatProperties.class})
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.OPENAI,
    matchIfMissing = true)
@ImportAutoConfiguration(classes = {SpringAiRetryAutoConfiguration.class,
    RestClientAutoConfiguration.class,
    WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class})
public class SpringAIConfig {



    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi(OpenAiConnectionProperties commonProperties,
        OpenAiChatProperties chatProperties,
        ObjectProvider<RestClient.Builder> restClientBuilderProvider,
        ObjectProvider<WebClient.Builder> webClientBuilderProvider,
        ResponseErrorHandler responseErrorHandler) {

        OpenAIAutoConfigurationUtil.ResolvedConnectionProperties resolved = resolveConnectionProperties(
            commonProperties, chatProperties, "chat");

        return OpenAiApi.builder()
            .baseUrl(resolved.baseUrl())
            .apiKey(new SimpleApiKey(resolved.apiKey()))
            .headers(resolved.headers())
            .completionsPath(chatProperties.getCompletionsPath())
            .embeddingsPath(OpenAiEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH)
            .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
            .webClientBuilder(webClientBuilderProvider.getIfAvailable(WebClient::builder))
            .responseErrorHandler(responseErrorHandler)
            .build();
    }

    @Bean
    public OpenAiChatModel springOpenChatModel(OpenAiApi openAiApi,
        OpenAiChatProperties chatProperties,
        ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
        ObjectProvider<ObservationRegistry> observationRegistry,
        ObjectProvider<ChatModelObservationConvention> observationConvention,
        ObjectProvider<ToolExecutionEligibilityPredicate> openAiToolExecutionEligibilityPredicate) {

        var chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(chatProperties.getOptions())
            .toolCallingManager(toolCallingManager)
            .toolExecutionEligibilityPredicate(
                openAiToolExecutionEligibilityPredicate.getIfUnique(
                    DefaultToolExecutionEligibilityPredicate::new))
            .retryTemplate(retryTemplate)
            .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
            .build();

        observationConvention.ifAvailable(chatModel::setObservationConvention);

        return chatModel;
    }

}
