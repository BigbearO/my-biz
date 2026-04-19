# TTS 接口功能说明与 Java 迁移方案
 

## 2 Java 实现方案

### 2.1 技术选型

| 项目     | 选型                      | 说明                        |
| -------- | ------------------------- | --------------------------- |
| 框架     | Spring Boot 3.3+ (JDK 17) |                             |
| 响应式   | Spring WebFlux            | 流式音频返回 `Flux<byte[]>` |
| TTS 通信 | Reactor Netty WebSocket   | 直连微软 Edge TTS WebSocket |
| 配置     | @ConfigurationProperties  | 类型安全，替代 @Value       |
| 校验     | Jakarta Validation        | @NotBlank 等注解            |

### 2.2 项目结构

```
src/main/java/com/avatar3d/
├── config/
│   └── TtsProperties.java           # TTS 配置属性类
├── controller/
│   └── TtsController.java           # GET /api/tts
└── service/
    └── EdgeTtsService.java          # WebSocket TTS 合成
```

### 2.3 依赖 (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot WebFlux -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring Boot Configuration Processor -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

### 2.4 配置 (application.yml)

```yaml
app:
  tts:
    trusted-client-token: 6A5AA1D4EAFF4E9FB37E23D68491D6F0
    ws-url: wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1
    voice: zh-CN-XiaoxiaoNeural
    rate: "+0%"
    pitch: "+0Hz"
    connect-timeout: 5000
    read-timeout: 30000
    audio-format: audio-24khz-48kbitrate-mono-mp3
```

### 2.5 配置属性类

```java
package com.avatar3d.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
    String trustedClientToken,
    String wsUrl,
    String voice,
    String rate,
    String pitch,
    int connectTimeout,
    int readTimeout,
    String audioFormat
) {
    public TtsProperties {
        if (voice == null || voice.isBlank()) voice = "zh-CN-XiaoxiaoNeural";
        if (rate == null || rate.isBlank()) rate = "+0%";
        if (pitch == null || pitch.isBlank()) pitch = "+0Hz";
        if (audioFormat == null || audioFormat.isBlank()) audioFormat = "audio-24khz-48kbitrate-mono-mp3";
        if (connectTimeout <= 0) connectTimeout = 5000;
        if (readTimeout <= 0) readTimeout = 30000;
    }
}
```

### 2.6 Edge TTS Service

```java
package com.avatar3d.service;

import com.avatar3d.config.TtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class EdgeTtsService {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsService.class);

    private final TtsProperties props;
    private final HttpClient httpClient;

    public EdgeTtsService(TtsProperties props) {
        this.props = props;

        this.httpClient = HttpClient.create()
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
        // 1. 参数回填（对应 Python: voice = voice or TTS_VOICE）
        voice = blankToDefault(voice, props.voice());
        rate  = blankToDefault(rate,  props.rate());
        pitch = blankToDefault(pitch, props.pitch());

        log.info("TTS 请求: voice={}, text={}", voice,
            text.length() > 30 ? text.substring(0, 30) + "..." : text);

        // 2. 构建 WebSocket URI
        String connectionId = UUID.randomUUID().toString();
        String wsUri = props.wsUrl()
            + "?Ocp-Apim-Subscription-Key=" + props.trustedClientToken()
            + "&ConnectionId=" + connectionId;

        // 3. 构建发送消息
        String configXml = buildConfigXml(voice, rate, pitch);
        String textMessage = buildTextMessage(text);

        // 4. 建立 WebSocket 连接，发送配置+文本，接收音频流
        return httpClient
            .websocket()
            .uri(wsUri)
            .connectTimeout(Duration.ofMillis(props.connectTimeout()))
            .handle((inbound, outbound) -> {
                // 发送 config + text 两条消息
                return outbound.send(
                    Flux.just(
                        ByteBuffer.wrap(configXml.getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.wrap(textMessage.getBytes(StandardCharsets.UTF_8))
                    )
                ).thenMany(
                    // 接收所有帧，过滤出音频二进制数据
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

    // ==================== 私有方法 ====================

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
```

### 2.7 TTS Controller

```java
package com.avatar3d.controller;

import com.avatar3d.service.EdgeTtsService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/tts")
@Validated
public class TtsController {

    private final EdgeTtsService edgeTtsService;

    public TtsController(EdgeTtsService edgeTtsService) {
        this.edgeTtsService = edgeTtsService;
    }

    /**
     * 文本转语音，流式返回 MP3 音频。
     * 对应 Python: GET /api/tts
     */
    @GetMapping(produces = "audio/mpeg")
    public ResponseEntity<Flux<byte[]>> textToSpeech(
            @RequestParam @NotBlank(message = "text 不能为空") String text,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String rate,
            @RequestParam(required = false) String pitch
    ) {
        // text.strip() — 对应 Python 的 text.strip()
        String trimmedText = text.strip();

        Flux<byte[]> audioFlux = edgeTtsService.synthesize(trimmedText, voice, rate, pitch);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header("Content-Disposition", "inline")
            .body(audioFlux);
    }
}
```

### 2.8 Python → Java 逻辑对照表

| #    | Python 逻辑                                              | Java 对应                                                  |
| ---- | -------------------------------------------------------- | ---------------------------------------------------------- |
| 1    | `voice = voice or TTS_VOICE`                             | `blankToDefault(voice, props.voice())`                     |
| 2    | `text.strip()`                                           | `text.strip()` (Java 12+ String.strip())                   |
| 3    | `edge_tts.Communicate(text, voice, rate, pitch)`         | `buildConfigXml()` + `buildTextMessage()` + WebSocket 连接 |
| 4    | `async for chunk in communicate.stream()`                | `inbound.receiveFrames().map(...)`                         |
| 5    | `if chunk["type"] == "audio": yield chunk["data"]`       | `.filter(bytes -> bytes.length > 0)` (WebSocket 帧已过滤)  |
| 6    | `StreamingResponse(generate(), media_type="audio/mpeg")` | `ResponseEntity<Flux<byte[]>>` + `produces = "audio/mpeg"` |
| 7    | `headers={"Content-Disposition": "inline"}`              | `.header("Content-Disposition", "inline")`                 |

### 2.9 接口兼容性验证

迁移后接口的请求/响应格式与 Python 版完全一致：

```
请求: GET /api/tts?text=你好&voice=zh-CN-XiaoxiaoNeural&rate=%2B0%25&pitch=%2B0Hz
响应: Content-Type: audio/mpeg, Content-Disposition: inline
      流式 MP3 二进制数据
```

前端无需任何修改。
