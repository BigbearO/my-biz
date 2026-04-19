package com.bigbear.ai.platform.trigger.api;

import com.bigbear.ai.platform.domain.vo.ChatRequest;
import com.bigbear.ai.platform.domain.vo.HealthResponse;
import com.bigbear.ai.platform.domain.vo.ModelsResponse;
import com.bigbear.ai.platform.domain.vo.VoiceInfo;
import com.bigbear.ai.platform.domain.vo.VoiceListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 音频聊天 REST 接口
 * <p>
 * 实现 docs/spec.md 中定义的 5 个 API 端点：
 * 1. GET  /api/health     — 健康检查
 * 2. POST /api/chat       — 流式聊天
 * 3. GET  /api/tts        — 文本转语音
 * 4. GET  /api/tts/voices — 语音列表
 * 5. GET  /api/models     — 模型列表
 * <p>
 * 所有业务逻辑内聚在本 Controller 中，不修改现有 Service 层。
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class AudioChatController {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

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

    @Value("${spring.ai.openai.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private static final String EDGE_TTS_VOICES_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
            + "?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F0";

    public AudioChatController(ChatClient chatClient,
                               ChatModel chatModel,
                               ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    // ==================== 1. 健康检查 ====================

    /**
     * GET /api/health
     * 检查服务状态及 AI 模型连接是否正常
     */
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

    // ==================== 2. 流式聊天 ====================

    /**
     * POST /api/chat
     * 流式聊天，逐 token 返回文本
     */
    @PostMapping(value = "/chat")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("");
        }

        //todo 这里用于测试
        if(1==1){
            return Flux.just("这是一段测试代码，一下子发一下");
        }

        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .advisors(specData -> specData.param(ChatMemory.CONVERSATION_ID, "1"))
                .user(request.getMessage());

            // 添加对话历史
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

            return spec.stream().content();
        } catch (Exception e) {
            log.error("聊天失败", e);
            return Flux.just("Error: " + e.getMessage());
        }
    }

    // ==================== 3. 文本转语音 ====================

    /**
     * GET /api/tts
     * 文本转语音，流式返回 MP3 音频
     */
    @GetMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<StreamingResponseBody> textToSpeech(
        @RequestParam String text,
        @RequestParam(required = false) String voice,
        @RequestParam(required = false) String rate,
        @RequestParam(required = false) String pitch) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text parameter cannot be blank");
        }

        String voiceName = (voice != null && !voice.isBlank()) ? voice : defaultVoice;
        String voiceRate = (rate != null && !rate.isBlank()) ? rate : defaultRate;
        String voicePitch = (pitch != null && !pitch.isBlank()) ? pitch : defaultPitch;

        String  voiceName2="zh-CN-YunjianNeural";
        Optional<Voice> voiceOptional = TTSVoice.provides()
            .stream()
            .filter(v -> voiceName2.equals(v.getShortName()))
            .findFirst();
        if (voiceOptional.isEmpty()) {
            throw new IllegalStateException("voice not found: " + voiceName);
        }
        Voice voiceData = voiceOptional.get();

        TTS tts = new TTS(voiceData, text.strip())
            .isRateLimited(true)
            .formatMp3()
            .voiceRate(voiceRate)
            .voicePitch(voicePitch);

        StreamingResponseBody stream = outputStream -> {
            try {
                ByteArrayOutputStream audioStream = tts.transToAudioStream();
                audioStream.writeTo(outputStream);
                outputStream.flush();
            } catch (Exception e) {
                log.error("TTS 合成失败", e);
                throw new RuntimeException("TTS 合成失败: " + e.getMessage(), e);
            }
        };

        return ResponseEntity.ok()
            .header("Content-Disposition", "inline")
            .body(stream);
    }

    // ==================== 4. 语音列表 ====================

    /**
     * GET /api/tts/voices
     * 获取可用语音列表
     */
    @GetMapping("/tts/voices")
    public Mono<VoiceListResponse> listVoices() {
        return fetchVoicesFromEdgeTts();
    }

    // ==================== 5. 模型列表 ====================

    /**
     * GET /api/models
     * 获取可用模型列表
     */
    @GetMapping("/models")
    public Mono<ModelsResponse> listModels() {
        return fetchModelsFromOllama();
    }

    // ==================== 私有方法: 语音列表 ====================

    /**
     * 从 Edge TTS 获取语音列表
     */
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
                    // 只保留中文语音
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

    // ==================== 私有方法: 模型列表 ====================

    /**
     * 从 Ollama REST API 获取模型列表
     */
    private Mono<ModelsResponse> fetchModelsFromOllama() {
        return Mono.fromCallable(() -> {
            try {
                String baseUrl = ollamaBaseUrl;
                // Ollama OpenAI 兼容模式地址，去掉 /v1 后缀
                if (baseUrl.endsWith("/v1")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
                }

                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("获取模型列表失败: status={}", response.statusCode());
                    return ModelsResponse.builder()
                        .current(currentModel)
                        .available(List.of(currentModel))
                        .build();
                }

                JsonNode root = objectMapper.readTree(response.body());
                List<String> models = new ArrayList<>();
                JsonNode modelsNode = root.get("models");
                if (modelsNode != null && modelsNode.isArray()) {
                    for (JsonNode modelNode : modelsNode) {
                        String name = modelNode.has("name") ? modelNode.get("name").asText() : "";
                        if (!name.isBlank()) {
                            models.add(name);
                        }
                    }
                }

                return ModelsResponse.builder()
                    .current(currentModel)
                    .available(models)
                    .build();
            } catch (Exception e) {
                log.error("获取模型列表失败", e);
                return ModelsResponse.builder()
                    .current(currentModel)
                    .available(List.of(currentModel))
                    .build();
            }
        });
    }
}
