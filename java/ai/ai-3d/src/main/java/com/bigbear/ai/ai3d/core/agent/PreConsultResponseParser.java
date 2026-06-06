package com.bigbear.ai.ai3d.core.agent;

import com.bigbear.ai.ai3d.domain.vo.SseChatEvent;
import com.bigbear.ai.ai3d.domain.vo.SseChatEvent.QuickOption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PreConsultResponseParser {

    private static final Pattern OPTIONS_PATTERN =
        Pattern.compile("@@OPTIONS@@\\s*(\\[.*?])\\s*", Pattern.DOTALL);
    private static final Pattern PHASE_PATTERN =
        Pattern.compile("@@PHASE@@(\\w+)@@");
    private static final Pattern SUMMARY_PATTERN =
        Pattern.compile("@@SUMMARY@@\\s*([\\s\\S]*?)\\s*(?=@@COMPLETED@@|$)");

    private final ObjectMapper objectMapper;

    public PreConsultResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedResponse parse(String raw) {
        String text = raw;
        List<QuickOption> options = null;
        String phase = null;
        boolean completed = false;
        String summary = null;

        // 解析选项
        Matcher optMatcher = OPTIONS_PATTERN.matcher(text);
        if (optMatcher.find()) {
            try {
                options = objectMapper.readValue(optMatcher.group(1),
                    new TypeReference<List<QuickOption>>() {});
            } catch (Exception e) {
                log.warn("解析选项失败: {}", e.getMessage());
            }
            text = optMatcher.replaceAll("").trim();
        }

        // 解析阶段
        Matcher phaseMatcher = PHASE_PATTERN.matcher(text);
        if (phaseMatcher.find()) {
            phase = phaseMatcher.group(1);
            text = phaseMatcher.replaceAll("").trim();
        }

        // 解析摘要
        Matcher summaryMatcher = SUMMARY_PATTERN.matcher(text);
        if (summaryMatcher.find()) {
            summary = summaryMatcher.group(1).trim();
            text = summaryMatcher.replaceAll("").trim();
        }

        // 解析完成标记
        if (text.contains("@@COMPLETED@@")) {
            completed = true;
            text = text.replace("@@COMPLETED@@", "").trim();
        }

        return new ParsedResponse(text, options, phase, completed, summary);
    }

    @Data
    @AllArgsConstructor
    public static class ParsedResponse {
        private String text;
        private List<QuickOption> options;
        private String phase;
        private boolean completed;
        private String summary;
    }
}
