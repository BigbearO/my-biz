//package com.bigbear.ai.ai3d.core.agent;
//
//import com.alibaba.cloud.ai.graph.RunnableConfig;
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
//import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
//import com.bigbear.ai.ai3d.domain.vo.SseChatEvent;
//import com.bigbear.ai.ai3d.domain.vo.SseChatEvent.QuickOption;
//import com.bigbear.ai.ai3d.infra.service.TtsService;
//import com.bigbear.ai.ai3d.core.agent.PreConsultResponseParser.ParsedResponse;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Component;
//import org.springframework.util.StreamUtils;
//import reactor.core.publisher.Flux;
//
//@Slf4j
//@Component
//public class PreConsultAgent {
//
//    private final ReactAgent agent;
//    private final PreConsultResponseParser parser;
//    private final TtsService ttsService;
//
//    public PreConsultAgent(ChatModel chatModel,
//                           PreConsultResponseParser parser,
//                           TtsService ttsService) throws IOException {
//        this.parser = parser;
//        this.ttsService = ttsService;
//
//        String systemPrompt = readPrompt("prompts/pre-consult-prompt.txt");
//
//        this.agent = ReactAgent.builder()
//            .name("pre_consult_agent")
//            .model(chatModel)
//            .systemPrompt(systemPrompt)
//            .saver(new MemorySaver())
//            .build();
//    }
//
//    /**
//     * 流式执行诊前对话，返回 SSE 事件流
//     *
//     * @param message        用户消息
//     * @param conversationId 会话ID，用于多轮记忆
//     * @return SSE 事件流
//     */
//    public Flux<SseChatEvent> streamChat(String message, String conversationId) {
//        RunnableConfig config = RunnableConfig.builder()
//            .threadId(conversationId)
//            .build();
//
//        StringBuilder fullText = new StringBuilder();
//
//        return Flux.create(sink -> {
//            try {
//                agent.stream(message, config)
//                    .subscribe(
//                        output -> {
//                            if (output instanceof StreamingOutput<?> streamingOutput) {
//                                String chunk = streamingOutput.chunk();
//                                if (chunk != null && !chunk.isEmpty()) {
//                                    fullText.append(chunk);
//                                    sink.next(SseChatEvent.chunk(chunk));
//                                }
//                            }
//                        },
//                        error -> {
//                            log.error("Agent 流式执行失败", error);
//                            sink.next(SseChatEvent.error(error.getMessage()));
//                            sink.next(SseChatEvent.done(false, null));
//                            sink.complete();
//                        },
//                        () -> {
//                            // 流结束，解析结构化数据
//                            ParsedResponse parsed = parser.parse(fullText.toString());
//
//                            // 推送选项
//                            List<QuickOption> options = parsed.getOptions();
//                            if (options != null && !options.isEmpty()) {
//                                sink.next(SseChatEvent.options(options));
//                            }
//
//                            // 推送阶段
//                            if (parsed.getPhase() != null) {
//                                sink.next(SseChatEvent.phase(parsed.getPhase()));
//                            }
//
//                            // 推送摘要
//                            if (parsed.getSummary() != null) {
//                                sink.next(SseChatEvent.summary(parsed.getSummary()));
//                            }
//
//                            // TTS 合成（只对纯文本部分）
//                            String audioBase64 = null;
//                            try {
//                                audioBase64 = ttsService.synthesizeToBase64(parsed.getText());
//                            } catch (Exception e) {
//                                log.warn("TTS 合成失败，跳过音频", e);
//                            }
//
//                            // 推送 done 事件
//                            sink.next(SseChatEvent.done(parsed.isCompleted(), audioBase64));
//                            sink.complete();
//                        }
//                    );
//            } catch (Exception e) {
//                log.error("Agent 创建流失败", e);
//                sink.next(SseChatEvent.error(e.getMessage()));
//                sink.complete();
//            }
//        });
//    }
//
//    private String readPrompt(String path) throws IOException {
//        ClassPathResource resource = new ClassPathResource(path);
//        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
//    }
//}
