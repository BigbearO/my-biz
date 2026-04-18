package com.bigbear.ai.platform.trigger.api;

import com.bigbear.ai.platform.domain.vo.ChatRequest;
import com.bigbear.ai.platform.domain.vo.HealthResponse;
import com.bigbear.ai.platform.domain.vo.ModelsResponse;
import com.bigbear.ai.platform.domain.vo.VoiceInfo;
import com.bigbear.ai.platform.domain.vo.VoiceListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.WebsocketClientSpec;

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

    @Value("${app.tts.read-timeout:30000}")
    private int readTimeout;

    @Value("${spring.ai.openai.chat.model:qwen2.5:7b}")
    private String currentModel;

    @Value("${spring.ai.openai.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private static final String EDGE_TTS_WS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
            + "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F0"
            + "&ConnectionId=";

    private static final String EDGE_TTS_VOICES_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
            + "?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F0";

    /**
     * Edge TTS 音频帧中 header 与 body 的分隔标记
     */
    private static final byte[] HEADER_BODY_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

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
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("");
        }

        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
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
    public Flux<DataBuffer> textToSpeech(
        @RequestParam(required = false) String text,
        @RequestParam(required = false) String voice,
        @RequestParam(required = false) String rate,
        @RequestParam(required = false) String pitch,
        ServerWebExchange exchange) {

        if (text == null || text.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return Flux.empty();
        }

        String v = (voice != null && !voice.isBlank()) ? voice : defaultVoice;
        String r = (rate != null && !rate.isBlank()) ? rate : defaultRate;
        String p = (pitch != null && !pitch.isBlank()) ? pitch : defaultPitch;

        return connectEdgeTts(text, v, r, p);
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

    // ==================== 私有方法: Edge TTS ====================

    /**
     * 连接 Edge TTS WebSocket，流式合成语音
     * <p>
     * Edge TTS WebSocket 协议:
     * 1. 客户端发送配置消息（text frame）
     * 2. 客户端发送 SSML 消息（text frame）
     * 3. 服务器返回音频数据（binary frame），格式: header\r\n\r\n+MP3二进制
     * 4. 服务器返回 turn.end（text frame）表示合成完成
     */
    private Flux<DataBuffer>  connectEdgeTts(String text, String voice, String rate, String pitch) {
        String connectionId = UUID.randomUUID().toString().replace("-", "");
        String requestId = UUID.randomUUID().toString().replace("-", "");
        DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

        Sinks.Many<byte[]> audioSink = Sinks.many().unicast().onBackpressureBuffer();

        // 构建配置消息
        String configMessage = "X-RequestId:" + requestId + "\r\n"
            + "Content-Type:application/json; charset=utf-8\r\n"
            + "Path:speech.config\r\n\r\n"
            + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
            + "{\"sentenceBoundaryEnabled\":\"false\"},\"outputFormat\":"
            + "\"audio-24khz-48kbitrate-mono-mp3\"}}}}";

        // 构建 SSML
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>"
            + "<voice name='" + voice + "'>"
            + "<prosody rate='" + rate + "' pitch='" + pitch + "'>"
            + escapeXml(text)
            + "</prosody></voice></speak>";

        String ssmlMessage = "X-RequestId:" + requestId + "\r\n"
            + "Content-Type:application/ssml+xml\r\n"
            + "Path:ssml\r\n\r\n"
            + ssml;

        reactor.netty.http.client.HttpClient.create()
            .websocket(WebsocketClientSpec.builder().build())
            .uri(EDGE_TTS_WS_URL + connectionId)
            .connect()
            .flatMapMany(session -> {
                // 依次发送配置消息和 SSML
                Flux<ByteBuf> outbound = Flux.just(
                    Unpooled.wrappedBuffer(configMessage.getBytes(StandardCharsets.UTF_8)),
                    Unpooled.wrappedBuffer(ssmlMessage.getBytes(StandardCharsets.UTF_8))
                );

                session.outbound().send(outbound);

                return session.inbound().receiveObject()
                    .cast(WebSocketFrame.class)
                    .doOnNext(frame -> {
                        if (frame.isFinalFragment()) {
                            // 处理文本帧: turn.end 等控制消息
                            String content = frame.content().toString(StandardCharsets.UTF_8);
                            if (content.contains("Path:turn.end")) {
                                audioSink.tryEmitComplete();
                            }
                        } else {
                            // 处理二进制帧: 音频数据
                            ByteBuf content = frame.content();
                            if (content.readableBytes() > 0) {
                                byte[] rawBytes = new byte[content.readableBytes()];
                                content.readBytes(rawBytes);
                                byte[] audioData = extractAudioFromBinary(rawBytes);
                                if (audioData != null && audioData.length > 0) {
                                    audioSink.tryEmitNext(audioData);
                                }
                            }
                        }
                    })
                    .doOnError(audioSink::tryEmitError)
                    .doOnComplete(audioSink::tryEmitComplete);
            })
            .subscribe(
                v -> {},
                err -> log.error("Edge TTS WebSocket 错误", err),
                () -> {}
            );

        return audioSink.asFlux()
            .map(bytes -> bufferFactory.wrap(bytes));
    }

    /**
     * 从 Edge TTS 二进制帧中提取 MP3 音频数据
     * <p>
     * 二进制帧格式: <header>\r\n\r\n<MP3 audio data>
     * header 中包含 Path:audio 等信息，body 是纯 MP3 二进制
     */
    private byte[] extractAudioFromBinary(byte[] rawBytes) {
        try {
            // 查找 \r\n\r\n 分隔符位置
            int separatorPos = findHeaderBodySeparator(rawBytes);
            if (separatorPos < 0) {
                return null;
            }
            int bodyStart = separatorPos + HEADER_BODY_SEPARATOR.length;
            if (bodyStart >= rawBytes.length) {
                return null;
            }
            byte[] audioData = new byte[rawBytes.length - bodyStart];
            System.arraycopy(rawBytes, bodyStart, audioData, 0, audioData.length);
            return audioData;
        } catch (Exception e) {
            log.warn("解析音频数据失败", e);
            return null;
        }
    }

    /**
     * 在字节数组中查找 \r\n\r\n 分隔符位置
     */
    private int findHeaderBodySeparator(byte[] data) {
        byte[] sep = HEADER_BODY_SEPARATOR;
        outer:
        for (int i = 0; i <= data.length - sep.length; i++) {
            for (int j = 0; j < sep.length; j++) {
                if (data[i + j] != sep[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * XML 特殊字符转义
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
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
