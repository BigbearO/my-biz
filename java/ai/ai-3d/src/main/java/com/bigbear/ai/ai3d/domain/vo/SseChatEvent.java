package com.bigbear.ai.ai3d.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseChatEvent {

    private String type;
    private String content;
    private List<QuickOption> items;
    private String value;
    private Boolean completed;
    private String audio;
    private String message;
    private SummaryFields fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickOption {
        private String label;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryFields {
        private String chiefComplaint;
        private String presentIllness;
        private String pastHistory;
        private String medication;
        private String allergy;
        private String visitGoal;
    }

    public static SseChatEvent chunk(String content) {
        return SseChatEvent.builder().type("chunk").content(content).build();
    }

    public static SseChatEvent options(List<QuickOption> items) {
        return SseChatEvent.builder().type("options").items(items).build();
    }

    public static SseChatEvent phase(String phase) {
        return SseChatEvent.builder().type("phase").value(phase).build();
    }

    public static SseChatEvent summary(String content) {
        return SseChatEvent.builder().type("summary").content(content).build();
    }

    public static SseChatEvent summary(String content, SummaryFields fields) {
        return SseChatEvent.builder().type("summary").content(content).fields(fields).build();
    }

    public static SseChatEvent done(boolean completed, String audio) {
        return SseChatEvent.builder().type("done").completed(completed).audio(audio).build();
    }

    public static SseChatEvent thinking() {
        return SseChatEvent.builder().type("thinking").build();
    }

    public static SseChatEvent audio(String audioBase64) {
        return SseChatEvent.builder().type("audio").audio(audioBase64).build();
    }

    public static SseChatEvent error(String message) {
        return SseChatEvent.builder().type("error").message(message).build();
    }
}
