package com.bigbear.ai.ai3d.core.agent;

import com.bigbear.ai.ai3d.core.agent.PreConsultResponseParser.ParsedResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PreConsultOutputValidator {

    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\\x{10000}-\\x{10FFFF}]|[\\u2600-\\u27BF]|[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]"
    );
    private static final Pattern ID_PATTERN = Pattern.compile(
        "\\d{17}[\\dXx]|\\d{15}|\\d{16,19}"
    );

    @Value("${pre-consult.max-response-length:80}")
    private int maxResponseLength;

    @Value("${pre-consult.min-options:2}")
    private int minOptions;

    @Value("${pre-consult.max-options:4}")
    private int maxOptions;

    public List<String> validate(ParsedResponse parsed, String currentPhase) {
        List<String> warnings = new ArrayList<>();

        if (parsed.getText() == null || parsed.getText().isBlank()) {
            warnings.add("回复内容为空");
        }

        if (parsed.getText() != null && parsed.getText().length() > maxResponseLength) {
            warnings.add("回复超长(" + parsed.getText().length() + "/" + maxResponseLength + ")");
        }

        if (parsed.getText() != null && EMOJI_PATTERN.matcher(parsed.getText()).find()) {
            warnings.add("回复包含emoji");
        }

        if (parsed.getText() != null && ID_PATTERN.matcher(parsed.getText()).find()) {
            warnings.add("回复可能包含身份证/银行卡号");
        }

        if (parsed.getPhase() == null) {
            warnings.add("缺少阶段标记(@@PHASE@@)");
        }

        boolean isSummaryPhase = "SUMMARY".equals(currentPhase) || "SUMMARY".equals(parsed.getPhase());

        if (isSummaryPhase) {
            if (parsed.getOptions() != null && !parsed.getOptions().isEmpty()) {
                warnings.add("SUMMARY阶段不应提供选项");
            }
        } else {
            if (parsed.getOptions() == null || parsed.getOptions().isEmpty()) {
                warnings.add("缺少选项");
            } else if (parsed.getOptions().size() < minOptions) {
                warnings.add("选项数不足(" + parsed.getOptions().size() + "/" + minOptions + ")");
            } else if (parsed.getOptions().size() > maxOptions) {
                warnings.add("选项数过多(" + parsed.getOptions().size() + "/" + maxOptions + ")");
            }
        }

        if (parsed.getSummary() != null && parsed.getStructuredSummary() != null) {
            if (!parsed.getStructuredSummary().isAllFieldsPresent()) {
                warnings.add("摘要字段不完整: " + parsed.getStructuredSummary().getMissingFields());
            }
        }

        return warnings;
    }
}
