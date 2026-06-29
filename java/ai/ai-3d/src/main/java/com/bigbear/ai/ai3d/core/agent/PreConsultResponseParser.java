package com.bigbear.ai.ai3d.core.agent;

import com.bigbear.ai.ai3d.domain.vo.SseChatEvent.QuickOption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        Pattern.compile("@@SUMMARY@@\\s*(\\{[\\s\\S]*?\\})\\s*(?=@@COMPLETED@@|$)");

    private static final List<String> REQUIRED_FIELDS = List.of(
        "主诉", "现病史", "既往史", "用药情况", "过敏史", "就诊诉求"
    );

    private static final Map<String, String> FIELD_KEY_MAP = Map.of(
        "主诉", "chiefComplaint",
        "现病史", "presentIllness",
        "既往史", "pastHistory",
        "用药情况", "medication",
        "过敏史", "allergy",
        "就诊诉求", "visitGoal"
    );

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
        StructuredSummary structuredSummary = null;

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

        Matcher phaseMatcher = PHASE_PATTERN.matcher(text);
        if (phaseMatcher.find()) {
            phase = phaseMatcher.group(1);
            text = phaseMatcher.replaceAll("").trim();
        }

        Matcher summaryMatcher = SUMMARY_PATTERN.matcher(text);
        if (summaryMatcher.find()) {
            summary = summaryMatcher.group(1).trim();
            structuredSummary = parseStructuredSummary(summary);
            text = summaryMatcher.replaceAll("").trim();
        }

        if (text.contains("@@COMPLETED@@")) {
            completed = true;
            text = text.replace("@@COMPLETED@@", "").trim();
        }

        return new ParsedResponse(text, options, phase, completed, summary, structuredSummary);
    }

    public StructuredSummary parseStructuredSummary(String jsonBlock) {
        try {
            JsonNode node = objectMapper.readTree(jsonBlock);
            Map<String, String> fields = FIELD_KEY_MAP.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getValue,
                    entry -> getFieldValue(node, entry.getKey())
                ));

            List<String> missingFields = FIELD_KEY_MAP.entrySet().stream()
                .filter(entry -> {
                    String val = getFieldValue(node, entry.getKey());
                    return val == null || val.isEmpty() || "未获取".equals(val);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            return new StructuredSummary(
                fields.get("chiefComplaint"),
                fields.get("presentIllness"),
                fields.get("pastHistory"),
                fields.get("medication"),
                fields.get("allergy"),
                fields.get("visitGoal"),
                missingFields
            );
        } catch (Exception e) {
            log.warn("解析JSON摘要失败: {}", e.getMessage());
            List<String> missing = new ArrayList<>(REQUIRED_FIELDS);
            return new StructuredSummary(null, null, null, null, null, null, missing);
        }
    }

    public String buildDisplayText(StructuredSummary ss) {
        if (ss == null) return "";
        StringBuilder sb = new StringBuilder();
        appendField(sb, "主诉", ss.getChiefComplaint());
        appendField(sb, "现病史", ss.getPresentIllness());
        appendField(sb, "既往史", ss.getPastHistory());
        appendField(sb, "用药情况", ss.getMedication());
        appendField(sb, "过敏史", ss.getAllergy());
        appendField(sb, "就诊诉求", ss.getVisitGoal());
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(label).append("：").append(value);
        }
    }

    private String getFieldValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return "未获取";
        }
        String val = field.asText().trim();
        return val.isEmpty() ? "未获取" : val;
    }

    @Data
    @AllArgsConstructor
    public static class ParsedResponse {
        private String text;
        private List<QuickOption> options;
        private String phase;
        private boolean completed;
        private String summary;
        private StructuredSummary structuredSummary;
    }

    @Data
    @AllArgsConstructor
    public static class StructuredSummary {
        private String chiefComplaint;
        private String presentIllness;
        private String pastHistory;
        private String medication;
        private String allergy;
        private String visitGoal;
        private List<String> missingFields;

        public boolean isAllFieldsPresent() {
            return missingFields == null || missingFields.isEmpty();
        }
    }
}
