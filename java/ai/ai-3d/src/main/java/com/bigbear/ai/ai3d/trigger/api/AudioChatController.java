package com.bigbear.ai.ai3d.trigger.api;

import com.bigbear.ai.ai3d.core.properties.TtsProperties;
import com.bigbear.ai.ai3d.domain.vo.ChatRequest;
import com.bigbear.ai.ai3d.domain.vo.HealthResponse;
import com.bigbear.ai.ai3d.domain.vo.ModelsResponse;
import com.bigbear.ai.ai3d.domain.vo.VoiceInfo;
import com.bigbear.ai.ai3d.domain.vo.VoiceListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;
import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AudioChatController {

    @Resource(name = "openAiChatClient")
    private   ChatClient chatClient;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TtsProperties ttsProperties;

    @Value("${app.tts.voice:zh-CN-XiaoxiaoNeural}")
    private String defaultVoice;

    @Value("${app.tts.rate:+0%}")
    private String defaultRate;

    @Value("${app.tts.pitch:+0Hz}")
    private String defaultPitch;

    @Value("${app.tts.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${spring.ai.openai.chat.model:qwen2.5:7b}")
    private String currentModel;

    private static final String EDGE_TTS_VOICES_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
            + "?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F0";

    @GetMapping("/health")
    public Mono<HealthResponse> healthCheck() {
        try {
            chatModel.call(new org.springframework.ai.chat.prompt.Prompt("hi"));
            return Mono.just(HealthResponse.builder()
                .status("ok")
                .model(currentModel)
                .build());
        } catch (Exception e) {
            log.warn("健康检查失败: {}", e.getMessage());
            return Mono.just(HealthResponse.builder()
                .status("error")
                .model(currentModel)
                .detail("Ollama 服务不可用: " + e.getMessage())
                .build());
        }
    }

    @PostMapping(value = "/chat")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("");
        }

        try {
            ChatClientRequestSpec spec = chatClient.prompt()
                .advisors(specData -> specData.param(ChatMemory.CONVERSATION_ID, request.getConversationId()))
                .user(request.getMessage());

            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                List<Message> messages = new ArrayList<>();
                for (ChatRequest.ChatHistoryMessage hist : request.getHistory()) {
                    if ("user".equalsIgnoreCase(hist.getRole())) {
                        messages.add(new UserMessage(hist.getContent()));
                    } else if ("assistant".equalsIgnoreCase(hist.getRole())) {
                        messages.add(new AssistantMessage(hist.getContent()));
                    }
                }
                if (!messages.isEmpty()) {
                    spec = spec.messages(messages);
                }
            }

            return spec.stream().content()
                .scan(new ThinkFilterState(), (state, chunk) -> state.process(chunk))
                .map(ThinkFilterState::getOutput)
                .filter(s -> !s.isEmpty());
        } catch (Exception e) {
            log.error("聊天失败", e);
            return Flux.just("Error: " + e.getMessage());
        }
    }

    @GetMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> textToSpeech(
        @RequestParam String text,
        @RequestParam(required = false) String voice,
        @RequestParam(required = false) String rate,
        @RequestParam(required = false) String pitch) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text parameter cannot be blank");
        }

        String voiceRate = (rate != null && !rate.isBlank()) ? rate : defaultRate;
        String voicePitch = (pitch != null && !pitch.isBlank()) ? pitch : defaultPitch;

        String voiceName2 = "zh-CN-YunjianNeural";
        log.info("TTS 请求: voice={}, text={}", voiceName2, text.substring(0, Math.min(30, text.length())));

        Optional<Voice> voiceOptional = TTSVoice.provides()
            .stream()
            .filter(v -> voiceName2.equals(v.getShortName()))
            .findFirst();
        if (voiceOptional.isEmpty()) {
            throw new IllegalStateException("voice not found: " + voiceName2);
        }
        Voice voiceData = voiceOptional.get();

        TTS tts = new TTS(voiceData, text.strip())
            .findHeadHook()
            .isRateLimited(true)
            .formatMp3()
            .voiceRate(voiceRate)
            .voicePitch(voicePitch);

        ByteArrayOutputStream audioStream = tts.transToAudioStream();
        byte[] audioBytes = audioStream.toByteArray();

        log.info("TTS 合成完成: {} bytes", audioBytes.length);

        return ResponseEntity.ok()
            .header("Content-Type", "audio/mpeg")
            .header("Content-Disposition", "inline")
            .header("Content-Length", String.valueOf(audioBytes.length))
            .body(audioBytes);
    }

    @GetMapping("/tts/voices")
    public Mono<VoiceListResponse> listVoices() {
        return fetchVoicesFromEdgeTts();
    }

    @GetMapping("/models")
    public Mono<ModelsResponse> listModels() {
        return Mono.just(ModelsResponse.builder()
            .current(currentModel)
            .available(ttsProperties.getModels())
            .build());
    }

    private Mono<VoiceListResponse> fetchVoicesFromEdgeTts() {
        return Mono.fromCallable(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EDGE_TTS_VOICES_URL))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("获取语音列表失败: status={}", response.statusCode());
                    return VoiceListResponse.builder()
                        .current(defaultVoice)
                        .voices(List.of())
                        .build();
                }

                JsonNode root = objectMapper.readTree(response.body());
                List<VoiceInfo> voices = new ArrayList<>();

                for (JsonNode node : root) {
                    String locale = node.has("Locale") ? node.get("Locale").asText() : "";
                    if (locale.startsWith("zh-")) {
                        voices.add(VoiceInfo.builder()
                            .name(node.has("ShortName") ? node.get("ShortName").asText() : "")
                            .gender(node.has("Gender") ? node.get("Gender").asText() : "")
                            .locale(locale)
                            .build());
                    }
                }

                return VoiceListResponse.builder()
                    .current(defaultVoice)
                    .voices(voices)
                    .build();
            } catch (Exception e) {
                log.error("获取语音列表失败", e);
                return VoiceListResponse.builder()
                    .current(defaultVoice)
                    .voices(List.of())
                    .build();
            }
        });
    }

    private static class ThinkFilterState {
        private final StringBuilder buffer = new StringBuilder();
        private boolean inThinkBlock = false;
        private String output = "";

        public ThinkFilterState process(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                output = chunk;
                return this;
            }

            buffer.append(chunk);
            StringBuilder result = new StringBuilder();
            int i = 0;

            while (i < buffer.length()) {
                if (!inThinkBlock) {
                    int thinkStart = buffer.indexOf("<think>", i);
                    if (thinkStart == -1) {
                        result.append(buffer.substring(i));
                        buffer.setLength(0);
                        break;
                    }
                    result.append(buffer.substring(i, thinkStart));
                    i = thinkStart + 7;
                    inThinkBlock = true;
                } else {
                    int thinkEnd = buffer.indexOf("</think>", i);
                    if (thinkEnd == -1) {
                        buffer.delete(0, i);
                        break;
                    }
                    i = thinkEnd + 8;
                    inThinkBlock = false;
                }
            }

            if (i >= buffer.length()) {
                buffer.setLength(0);
            }

            output = result.toString();
            return this;
        }

        public String getOutput() {
            return output;
        }
    }
}
