package com.bigbear.goofish.app;

import static java.lang.invoke.MethodHandles.lookup;

import cn.hutool.extra.spring.SpringUtil;
import com.bigbear.ai.biz.manager.AiServiceContainer;
import com.bigbear.ai.core.IAiService;
import com.bigbear.goofish.core.socket.msg.MsgDispatcher;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AutoReplyService implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(lookup().lookupClass());

    @Value("${goodfish.system-prompt}")
    Resource systemPromptResource;

    public String systemPrompt = "你是一个电商平台客户";

    @Value("${goodfish.emotion-prompt}")
    Resource emotionPromptResource;
    @Getter
    public String emotionPrompt;


    public Mono<String> generateReply(String prompt, MsgDispatcher.MsgContext ctx) {
        logger.info("send prompt to ai host: {}", prompt);

        AiServiceContainer serviceContainer = SpringUtil.getBean(AiServiceContainer.class);

        IAiService aiService = serviceContainer.get(ctx.getItemId(), IAiService.class);

        // 调用LLM进行回复
        String aiResponse = aiService.chatWithSystem(ctx.getItemId()+":"+ctx.getChatId(), systemPrompt, ctx.getSendMessage());

        return Mono.just(aiResponse);
        // 提供一个简化的替代实现，返回默认回复
//        return Mono.fromCallable(() -> "这是一个自动回复：我收到了您的消息。")
//                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.systemPrompt = new String(FileCopyUtils.copyToByteArray(systemPromptResource.getInputStream()));
        this.emotionPrompt = new String(FileCopyUtils.copyToByteArray(emotionPromptResource.getInputStream()));
    }
}
