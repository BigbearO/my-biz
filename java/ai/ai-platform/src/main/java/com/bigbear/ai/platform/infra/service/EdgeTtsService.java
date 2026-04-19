package com.bigbear.ai.platform.infra.service;

import com.bigbear.ai.platform.config.TtsProperties;
import com.bigbear.ai.platform.domain.vo.VoiceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Edge TTS 语音合成服务
 * <p>
 * 通过 Reactor Netty WebSocket 直连微软 Edge TTS，流式合成 MP3 音频。
 * 对应 docs/tts.md 2.6 节。
 */
@Slf4j
@Service
public class EdgeTtsService {

    private static final String EDGE_TTS_VOICES_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
            + "?trustedclienttoken=";

    private final TtsProperties props;
    private final reactor.netty.http.client.HttpClient reactorHttpClient;
    private final ObjectMapper objectMapper;

    public EdgeTtsService(TtsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.reactorHttpClient = reactor.netty.http.client.HttpClient.create()
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectTimeout())
            .responseTimeout(Duration.ofMillis(props.readTimeout()));
    }

    /**
     * 流式合成 TTS，返回 MP3 字节流。
     * 对应 Python: edge_tts.Communicate(text, voice, rate, pitch).stream()
     *
     * @param text  要合成的文本（调用前已做 strip）
     * @param voice 语音名称，null 时使用默认值
     * @param rate  语速，null 时使用默认值
     * @param pitch 音调，null 时使用默认值
     * @return Flux<byte[]> MP3 音频数据块流
     */
    public Flux<byte[]> synthesize(String text, String voice, String rate, String pitch) {
        voice = blankToDefault(voice, props.voice());
        rate = blankToDefault(rate, props.rate());
        pitch = blankToDefault(pitch, props.pitch());

        log.info("Edge TTS 请求: voice={}, text={}", voice,
            text.length() > 30 ? text.substring(0, 30) + "..." : text);

        String connectionId = UUID.randomUUID().toString();
        String wsUri = props.wsUrl()
            + "?Ocp-Apim-Subscription-Key=" + props.trustedClientToken()
            + "&ConnectionId=" + connectionId;

        String configXml = buildConfigXml(voice, rate, pitch);
        String textMessage = buildTextMessage(text);

        return reactorHttpClient
            .websocket()
            .uri(wsUri)
            .handle((inbound, outbound) -> {
                var configBuf = io.netty.buffer.Unpooled.wrappedBuffer(configXml.getBytes(StandardCharsets.UTF_8));
                var textBuf = io.netty.buffer.Unpooled.wrappedBuffer(textMessage.getBytes(StandardCharsets.UTF_8));
                return outbound.send(
                    reactor.core.publisher.Flux.just(configBuf, textBuf)
                ).then().thenMany(
                    inbound.aggregateFrames()
                        .receiveFrames()
                        .map(frame -> {
                            var content = frame.content();
                            if (content.readableBytes() > 0) {
                                byte[] bytes = new byte[content.readableBytes()];
                                content.readBytes(bytes);
                                return bytes;
                            }
                            return new byte[0];
                        })
                        .filter(bytes -> bytes.length > 0)
                );
            })
            .takeUntil(bytes -> bytes.length == 0)
            .onErrorResume(e -> {
                log.error("Edge TTS WebSocket 异常: {}", e.getMessage(), e);
                return Flux.error(new RuntimeException("Edge TTS 合成失败: " + e.getMessage(), e));
            });
    }

    /**
     * 获取可用语音列表（仅中文）
     *
     * @return 语音信息列表
     */
    public List<VoiceInfo> listVoices() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeout()))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EDGE_TTS_VOICES_URL + props.trustedClientToken()))
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("获取语音列表失败: status={}", response.statusCode());
                return List.of();
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
            return voices;
        } catch (Exception e) {
            log.error("获取语音列表失败", e);
            return List.of();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前默认语音名称
     */
    public String getCurrentVoice() {
        return props.voice();
    }

    /**
     * 构建 SSML 配置 XML。
     * 对应 edge_tts 内部构建的 config 消息。
     */
    private String buildConfigXml(String voice, String rate, String pitch) {
        return String.format(
            "<synth xmlns=\"http://www.weblingo.com/ws/2001/10/synthesis\">" +
            "<voice name=\"%s\">%s,%s</voice>" +
            "<container src=\"text\"/>" +
            "<context>" +
            "<synthesis audiooutputformat=\"%s\">" +
            "<audio xmlns=\"http://www.weblingo.com/ws/2001/10/synthesis\">" +
            "<output formatType=\"audio/mp3\"/>" +
            "</audio>" +
            "</synthesis>" +
            "</context>" +
            "</synth>",
            voice, rate, pitch, props.audioFormat()
        );
    }

    /**
     * 构建文本合成消息。
     * 对应 edge_tts 发送的 X-TTS 消息。
     */
    private String buildTextMessage(String text) {
        return "X-TTS: {\"type\":\"text\",\"data\":\"" + escapeJson(text) +
            "\",\"volume\":\"\",\"rate\":\"\",\"pitch\":\"\"}";
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * 空白字符串回填默认值。
     * 对应 Python: voice = voice or TTS_VOICE
     */
    private String blankToDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
