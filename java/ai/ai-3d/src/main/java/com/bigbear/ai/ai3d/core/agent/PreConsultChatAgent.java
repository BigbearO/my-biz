package com.bigbear.ai.ai3d.core.agent;

import cn.hutool.extra.spring.SpringUtil;
import com.bigbear.ai.ai3d.core.agent.PreConsultResponseParser.ParsedResponse;
import com.bigbear.ai.ai3d.core.agent.PreConsultResponseParser.StructuredSummary;
import com.bigbear.ai.ai3d.domain.entity.PreConsultSummaryEntity;
import com.bigbear.ai.ai3d.domain.vo.SseChatEvent;
import com.bigbear.ai.ai3d.domain.vo.SseChatEvent.QuickOption;
import com.bigbear.ai.ai3d.domain.vo.SseChatEvent.SummaryFields;
import com.bigbear.ai.ai3d.infra.manager.PreConsultSummaryManager;
import com.bigbear.ai.ai3d.infra.memory.CustomChatMemory;
import com.bigbear.ai.ai3d.infra.service.TtsService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class PreConsultChatAgent {

    private final ChatClient chatClient;
    private final PreConsultResponseParser parser;
    private final PreConsultOutputValidator validator;
    private final PreConsultPhaseTracker phaseTracker;
    private final PreConsultSummaryManager summaryManager;
    private final TtsService ttsService;

    @Value("${pre-consult.round-nudge-at:12}")
    private int roundNudgeAt;

    @Value("${pre-consult.round-force-at:14}")
    private int roundForceAt;

    @Value("${pre-consult.max-rounds:15}")
    private int maxRounds;

    public PreConsultChatAgent(ChatModel chatModel,
        CustomChatMemory chatMemory,
        PreConsultResponseParser parser,
        PreConsultOutputValidator validator,
        PreConsultPhaseTracker phaseTracker,
        PreConsultSummaryManager summaryManager,
        TtsService ttsService) throws IOException {
        this.parser = parser;
        this.validator = validator;
        this.phaseTracker = phaseTracker;
        this.summaryManager = summaryManager;
        this.ttsService = ttsService;

        String systemPrompt = readPrompt("prompts/pre-consult-prompt.txt");

        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .apiKey(SpringUtil.getProperty("llm.chat.ak"))
            .temperature(0.25)
            .model(SpringUtil.getProperty("llm.chat.model"))
            .baseUrl(SpringUtil.getProperty("llm.chat.url")).build();

        ChatModel mychatModel = OpenAiChatModel.builder()
            .options(options)
            .build();

        this.chatClient = ChatClient.builder(mychatModel)
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }

    public String chat(String message, String conversationId) {

        int rounds = phaseTracker.incrementRound(conversationId);
        String currentPhase = phaseTracker.getCurrentPhase(conversationId);

        String enrichedMessage = enrichMessage(message, conversationId, rounds, currentPhase);
        CallResponseSpec call = chatClient.prompt()
            .user(enrichedMessage)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call();
        return call.content();

    }

    /**
     * 流式聊天方法，支持 SSE 推送
     * <p>
     * 使用 Spring AI 2.0 的流式调用，通过 ChatResponse 获取完整的响应对象，
     * 确保 MessageChatMemoryAdvisor 能够正确访问 conversationId 参数。
     *
     * @param message        用户消息
     * @param conversationId 会话 ID，不能为 null 或空字符串
     * @return SSE 事件流
     */
    public Flux<SseChatEvent> streamChat(String message, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            log.warn("conversationId 为空，无法执行流式聊天");
            return Flux.just(
                SseChatEvent.error("conversationId 不能为空"),
                SseChatEvent.done(false, null)
            );
        }

        int rounds = phaseTracker.incrementRound(conversationId);
        String currentPhase = phaseTracker.getCurrentPhase(conversationId);

        String enrichedMessage = enrichMessage(message, conversationId, rounds, currentPhase);

        StringBuilder fullText = new StringBuilder();

        return chatClient.prompt()
            .user(enrichedMessage)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
            .stream()
            .chatResponse()
            .doOnNext(response -> {
                if (response.getResult() != null) {
                    String chunk = response.getResult().getOutput().getText();
                    if (StringUtils.hasLength(chunk)) {
                        fullText.append(chunk);
                    }
                }
            })
            .map(response -> {
                if (response.getResult() != null) {
                    return response.getResult().getOutput().getText();
                }
                return "";
            })
            .scan(new StreamingContentFilter(), StreamingContentFilter::process)
            .map(StreamingContentFilter::getOutput)
            .filter(s -> !s.isEmpty())
            .map(SseChatEvent::chunk)
            .concatWith(Flux.defer(() -> {
                String fullResponse = fullText.toString();
                ParsedResponse parsed = parser.parse(fullResponse);

                // 阶段推进
                if (parsed.getPhase() != null) {
                    phaseTracker.advancePhase(conversationId, parsed.getPhase());
                }

                // 输出校验（仅 warn 不断流）
                List<String> warnings = validator.validate(parsed, currentPhase);
                if (!warnings.isEmpty()) {
                    log.warn("输出校验警告, conversationId={}: {}", conversationId, warnings);
                }

                Flux<SseChatEvent> extraEvents = Flux.empty();

                List<QuickOption> options = parsed.getOptions();
                if (options != null && !options.isEmpty()) {
                    extraEvents = extraEvents.concatWith(Flux.just(SseChatEvent.options(options)));
                }

                if (parsed.getPhase() != null) {
                    extraEvents = extraEvents.concatWith(Flux.just(SseChatEvent.phase(parsed.getPhase())));
                }

                if (parsed.getSummary() != null && parsed.getStructuredSummary() != null) {
                    StructuredSummary ss = parsed.getStructuredSummary();

                    String displayText = parser.buildDisplayText(ss);
                    SummaryFields fields = SummaryFields.builder()
                        .chiefComplaint(ss.getChiefComplaint())
                        .presentIllness(ss.getPresentIllness())
                        .pastHistory(ss.getPastHistory())
                        .medication(ss.getMedication())
                        .allergy(ss.getAllergy())
                        .visitGoal(ss.getVisitGoal())
                        .build();

                    extraEvents = extraEvents.concatWith(
                        Flux.just(SseChatEvent.summary(displayText, fields))
                    );

                    saveSummary(conversationId, ss, parsed.getSummary());
                }

                String ttsText = stripThinkTags(parsed.getText());
                String audioBase64 = null;
                try {
                    audioBase64 = ttsService.synthesizeToBase64(ttsText);
                } catch (Exception e) {
                    log.warn("TTS 合成失败，跳过音频", e);
                }

                extraEvents = extraEvents.concatWith(
                    Flux.just(SseChatEvent.done(parsed.isCompleted(), audioBase64))
                );

                return extraEvents;
            }))
            .onErrorResume(error -> {
                log.error("ChatClient 流式执行失败, conversationId={}", conversationId, error);
                return Flux.just(
                    SseChatEvent.error(error.getMessage()),
                    SseChatEvent.done(false, null)
                );
            });
    }

    private String enrichMessage(String message, String conversationId, int rounds, String currentPhase) {
        StringBuilder sb = new StringBuilder(message);

        if (rounds > 0) {
            sb.append("\n[context] currentPhase=").append(currentPhase)
                .append(", round=").append(rounds).append(" [/context]");
        }

        if (rounds >= roundNudgeAt) {
            sb.append("\n[instruction] 对话即将结束，请准备进入 SUMMARY 阶段并生成摘要 [/instruction]");
        }

        if (rounds >= roundForceAt) {
            sb.append("\n[instruction] 本轮必须完成问诊，输出 @@SUMMARY@@ + @@COMPLETED@@ 标记 [/instruction]");
        }

        return sb.toString();
    }

    private void saveSummary(String conversationId, StructuredSummary ss, String rawSummary) {
        try {
            PreConsultSummaryEntity entity = new PreConsultSummaryEntity();
            entity.setChatId(conversationId);
            entity.setChiefComplaint(ss.getChiefComplaint());
            entity.setPresentIllness(ss.getPresentIllness());
            entity.setPastHistory(ss.getPastHistory());
            entity.setMedication(ss.getMedication());
            entity.setAllergy(ss.getAllergy());
            entity.setVisitGoal(ss.getVisitGoal());
            entity.setRawSummary(rawSummary);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            summaryManager.saveSummary(entity);
            log.info("摘要已保存, conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("保存摘要失败, conversationId={}", conversationId, e);
        }
    }

    private String readPrompt(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String stripThinkTags(String text) {
        return text.replaceAll("<think[\\s\\S]*?</think\\s*>", "").trim();
    }

    /**
     * 流式内容过滤器，过滤以下标记，不在前端文字框展示： - &lt;think/&gt; 块：思考过程，不展示、不参与 TTS - @@OPTIONS@@[...]：选项内容，只通过 SseChatEvent.options() 推送 -
     * @@PHASE@@阶段名@@：阶段标记，只通过 SseChatEvent.phase() 推送 - @@SUMMARY@@...@@COMPLETED@@：摘要内容，只通过 SseChatEvent.summary() 推送 -
     * @@COMPLETED@@：完成标记，只通过 SseChatEvent.done() 推送
     */
    private static class StreamingContentFilter {

        private static final String[] MARKER_PREFIXES = {
            "<think", "@@OPTIONS@@", "@@PHASE@@", "@@SUMMARY@@", "@@COMPLETED@@"
        };
        private static final int MAX_MARKER_LEN = 13;

        private final StringBuilder buffer = new StringBuilder();
        private boolean inThinkBlock = false;
        private boolean inOptionsBlock = false;
        private boolean inPhaseBlock = false;
        private boolean inSummaryBlock = false;
        private int bracketDepth = 0;
        private String output = "";

        public StreamingContentFilter process(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                output = chunk;
                return this;
            }

            buffer.append(chunk);
            StringBuilder result = new StringBuilder();
            int i = 0;

            while (i < buffer.length()) {
                if (inThinkBlock) {
                    int thinkEnd = buffer.indexOf("</think", i);
                    if (thinkEnd == -1) {
                        buffer.delete(0, i);
                        break;
                    }
                    int tagEnd = buffer.indexOf(">", thinkEnd);
                    if (tagEnd == -1) {
                        buffer.delete(0, i);
                        break;
                    }
                    i = tagEnd + 1;
                    inThinkBlock = false;
                } else if (inOptionsBlock) {
                    char c = buffer.charAt(i);
                    if (c == '[') {
                        bracketDepth++;
                    } else if (c == ']') {
                        bracketDepth--;
                        if (bracketDepth == 0) {
                            i++;
                            inOptionsBlock = false;
                            continue;
                        }
                    }
                    i++;
                } else if (inPhaseBlock) {
                    int closingAt = buffer.indexOf("@@", i);
                    if (closingAt == -1) {
                        buffer.delete(0, i);
                        break;
                    }
                    i = closingAt + 2;
                    inPhaseBlock = false;
                } else if (inSummaryBlock) {
                    int completedAt = buffer.indexOf("@@COMPLETED@@", i);
                    if (completedAt == -1) {
                        buffer.delete(0, i);
                        break;
                    }
                    i = completedAt + 13;
                    inSummaryBlock = false;
                } else {
                    int thinkStart = buffer.indexOf("<think", i);
                    int optionsStart = buffer.indexOf("@@OPTIONS@@", i);
                    int phaseStart = buffer.indexOf("@@PHASE@@", i);
                    int summaryStart = buffer.indexOf("@@SUMMARY@@", i);
                    int completedStart = buffer.indexOf("@@COMPLETED@@", i);

                    int nextMarker = -1;
                    int markerType = 0;

                    if (thinkStart != -1 && (nextMarker == -1 || thinkStart < nextMarker)) {
                        nextMarker = thinkStart;
                        markerType = 1;
                    }
                    if (optionsStart != -1 && (nextMarker == -1 || optionsStart < nextMarker)) {
                        nextMarker = optionsStart;
                        markerType = 2;
                    }
                    if (phaseStart != -1 && (nextMarker == -1 || phaseStart < nextMarker)) {
                        nextMarker = phaseStart;
                        markerType = 3;
                    }
                    if (summaryStart != -1 && (nextMarker == -1 || summaryStart < nextMarker)) {
                        nextMarker = summaryStart;
                        markerType = 4;
                    }
                    if (completedStart != -1 && (nextMarker == -1 || completedStart < nextMarker)) {
                        nextMarker = completedStart;
                        markerType = 5;
                    }

                    if (nextMarker == -1) {
                        String remaining = buffer.substring(i);
                        int holdBack = findHoldBackLength(remaining);
                        if (holdBack > 0) {
                            result.append(remaining, 0, remaining.length() - holdBack);
                            buffer.delete(0, buffer.length() - holdBack);
                        } else {
                            result.append(remaining);
                            buffer.setLength(0);
                        }
                        break;
                    }

                    result.append(buffer.substring(i, nextMarker));

                    switch (markerType) {
                        case 1:
                            int thinkTagEnd = buffer.indexOf(">", nextMarker);
                            if (thinkTagEnd != -1) {
                                i = thinkTagEnd + 1;
                            } else {
                                i = nextMarker + 6;
                            }
                            inThinkBlock = true;
                            break;
                        case 2:
                            i = nextMarker + 11;
                            inOptionsBlock = true;
                            bracketDepth = 0;
                            break;
                        case 3:
                            i = nextMarker + 9;
                            inPhaseBlock = true;
                            break;
                        case 4:
                            i = nextMarker + 10;
                            inSummaryBlock = true;
                            break;
                        case 5:
                            i = nextMarker + 13;
                            break;
                        default:
                            break;
                    }
                }
            }

            if (!inThinkBlock && !inOptionsBlock && !inPhaseBlock && !inSummaryBlock
                && i >= buffer.length()) {
                buffer.setLength(0);
            }

            output = result.toString();
            return this;
        }

        public String getOutput() {
            return output;
        }

        private int findHoldBackLength(String remaining) {
            int maxCheck = Math.min(remaining.length(), MAX_MARKER_LEN - 1);
            for (int len = maxCheck; len >= 1; len--) {
                String tail = remaining.substring(remaining.length() - len);
                for (String marker : MARKER_PREFIXES) {
                    if (marker.startsWith(tail)) {
                        return len;
                    }
                }
            }
            return 0;
        }
    }
}
