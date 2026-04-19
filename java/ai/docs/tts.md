# Edge TTS 服务 - Java Spring Boot 实现指南

## 一、当前 Python 实现（参考）

### 依赖

```
edge-tts>=7.0.0
fastapi>=0.115.0
```

### 核心代码

```python
# main.py
@app.get("/api/tts")
async def text_to_speech(
    text: str = Query(...),
    voice: str = Query(None),   # 默认 zh-CN-XiaoxiaoNeural
    rate: str = Query(None),    # 默认 +0%
    pitch: str = Query(None),   # 默认 +0Hz
):
    voice = voice or TTS_VOICE
    rate = rate or TTS_RATE
    pitch = pitch or TTS_PITCH

    async def generate():
        communicate = edge_tts.Communicate(
            text=text.strip(),
            voice=voice,
            rate=rate,
            pitch=pitch,
        )
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                yield chunk["data"]

    return StreamingResponse(
        generate(),
        media_type="audio/mpeg",
        headers={"Content-Disposition": "inline"},
    )
```

### 配置（config.py）

```python
TTS_VOICE = os.getenv("TTS_VOICE", "zh-CN-XiaoxiaoNeural")
TTS_RATE = os.getenv("TTS_RATE", "+0%")
TTS_PITCH = os.getenv("TTS_PITCH", "+0Hz")
```

---

## 二、Edge TTS 协议说明

edge-tts 是微软 Edge 浏览器"大声朗读"功能背后的 TTS 接口，**完全免费，无需注册，无需 API Key**。

### 关键凭证

| 项目 | 值 |
|------|-----|
| TRUSTED_CLIENT_TOKEN | `6A5AA1D4EAFF4E9FB37E23D68491D6F0` |
| WebSocket 地址 | `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1` |
| 语音列表接口 | `https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F0` |

### WebSocket 通讯流程

**Step 1 - 建立连接：**

```
wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1
  ?Ocp-Apim-Subscription-Key=6A5AA1D4EAFF4E9FB37E23D68491D6F0
  &ConnectionId={UUID}
```

**Step 2 - 发送 config（text/plain）：**

```xml
<synth xmlns="http://www.weblingo.com/ws/2001/10/synthesis">
  <voice name="zh-CN-XiaoxiaoNeural">+0%,+0Hz</voice>
  <container src="text"/>
  <context>
    <synthesis audiooutputformat="audio-24khz-48kbitrate-mono-mp3">
      <audio xmlns="http://www.weblingo.com/ws/2001/10/synthesis">
        <output formatType="audio/mp3"/>
      </audio>
    </synthesis>
  </context>
</synth>
```

**Step 3 - 发送文本（text/plain）：**

```
X-TTS: {"type":"text","data":"要合成的文本","volume":"","rate":"","pitch":""}
```

**Step 4 - 接收音频：** 服务端返回二进制 MP3 数据块

**Step 5 - 结束：** 发送 `X-TTS: {"type":"close"}` 或关闭 WebSocket

---

## 三、Java Spring Boot 实现步骤

### Step 1 - 添加依赖（pom.xml）

```xml
<dependencies>
    <!-- Spring WebFlux（流式响应） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- JWT / WebSocket（部分系统需要） -->
    <dependency>
        <groupId>io.projectreactor.netty</groupId>
        <artifactId>reactor-netty</artifactId>
    </dependency>

    <!-- 配置管理 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### Step 2 - 配置文件（application.yml）

```yaml
app:
  tts:
    # edge-tts 凭证（公开，无需注册）
    trusted-client-token: 6A5AA1D4EAFF4E9FB37E23D68491D6F0
    # 默认语音
    voice: zh-CN-XiaoxiaoNeural
    # 默认语速
    rate: "+0%"
    # 默认音调
    pitch: "+0Hz"
    # WebSocket 连接超时（ms）
    connect-timeout: 5000
    # 读取超时（ms）
    read-timeout: 30000
```

### Step 3 - 实体类

```java
// TtsRequest.java
public record TtsRequest(
    String text,
    String voice,
    String rate,
    String pitch
) {
    public TtsRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        voice = (voice == null || voice.isBlank()) ? "zh-CN-XiaoxiaoNeural" : voice;
        rate = (rate == null || rate.isBlank()) ? "+0%" : rate;
        pitch = (pitch == null || pitch.isBlank()) ? "+0Hz" : pitch;
    }
}
```

### Step 4 - WebSocket TTS 服务

```java
// EdgeTtsService.java
package com.avatar3d.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EdgeTtsService {

    private static final String WS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/websocket/v1"
        + "?Ocp-Apim-Subscription-Key=6A5AA1D4EAFF4E9FB37E23D68491D6F0";

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

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.create()
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
            .responseTimeout(Duration.ofMillis(readTimeout))
            .followRedirect(false);
    }

    /**
     * 流式合成 TTS，返回 MP3 字节流
     *
     * @param text 要合成的文本
     * @param voice 语音名称（如 zh-CN-XiaoxiaoNeural）
     * @param rate 语速（如 +0%、+10%）
     * @param pitch 音调（如 +0Hz）
     * @return Flux<byte[]> MP3 音频块
     */
    public Flux<byte[]> synthesize(String text, String voice, String rate, String pitch) {
        voice = (voice == null || voice.isBlank()) ? defaultVoice : voice;
        rate = (rate == null || rate.isBlank()) ? defaultRate : rate;
        pitch = (pitch == null || pitch.isBlank()) ? defaultPitch : pitch;

        String connectionId = UUID.randomUUID().toString();

        String configXml = buildConfigXml(voice, rate, pitch);
        String textJson = buildTextJson(text);

        AtomicBoolean connectionSent = new AtomicBoolean(false);
        AtomicReference<String> errorRef = new AtomicReference<>();

        return httpClient
            .websocket()
            .uri(WS_URL + "&ConnectionId=" + connectionId)
            .connectTimeout(Duration.ofMillis(connectTimeout))
            .responseTimeout(Duration.ofMillis(readTimeout))
            .handle((in, out) -> {
                if (errorRef.get() != null) {
                    return Flux.error(new RuntimeException(errorRef.get()));
                }

                if (!connectionSent.get()) {
                    connectionSent.set(true);
                    return out.send(
                        Flux.just(
                            ByteBuffer.wrap(configXml.getBytes()),
                            ByteBuffer.wrap(textJson.getBytes())
                        )
                    ).thenMany(in.aggregateFrames()
                        .receiveFrames()
                        .map(frame -> {
                            if (frame.content() instanceof io.netty.buffer.ByteBuf byteBuf) {
                                byte[] bytes = new byte[byteBuf.readableBytes()];
                                byteBuf.readBytes(bytes);
                                return bytes;
                            }
                            return new byte[0];
                        })
                        .filter(bytes -> bytes.length > 0)
                    );
                }

                return Flux.empty();
            })
            .takeUntil(bytes -> bytes.length == 0)
            .onErrorResume(e -> {
                errorRef.set(e.getMessage());
                return Flux.error(new RuntimeException("Edge TTS 连接失败: " + e.getMessage(), e));
            });
    }

    private String buildConfigXml(String voice, String rate, String pitch) {
        return String.format(
            "<synth xmlns=\"http://www.weblingo.com/ws/2001/10/synthesis\">" +
            "<voice name=\"%s\">%s,%s</voice>" +
            "<container src=\"text\"/>" +
            "<context>" +
            "<synthesis audiooutputformat=\"audio-24khz-48kbitrate-mono-mp3\">" +
            "<audio xmlns=\"http://www.weblingo.com/ws/2001/10/synthesis\">" +
            "<output formatType=\"audio/mp3\"/>" +
            "</audio>" +
            "</synthesis>" +
            "</context>" +
            "</synth>",
            voice, rate, pitch
        );
    }

    private String buildTextJson(String text) {
        return "X-TTS: {\"type\":\"text\",\"data\":\"" + escapeJson(text) + "\",\"volume\":\"\",\"rate\":\"\",\"pitch\":\"\"}";
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    @PreDestroy
    public void destroy() {
        // 清理资源
    }
}
```

### Step 5 - Controller

```java
// TtsController.java
package com.avatar3d.controller;

import com.avatar3d.service.EdgeTtsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final EdgeTtsService edgeTtsService;

    public TtsController(EdgeTtsService edgeTtsService) {
        this.edgeTtsService = edgeTtsService;
    }

    @GetMapping
    public ResponseEntity<Flux<byte[]>> tts(
            @RequestParam String text,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String rate,
            @RequestParam(required = false) String pitch
    ) {
        Flux<byte[]> audioFlux = edgeTtsService.synthesize(text, voice, rate, pitch);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header("Content-Disposition", "inline")
            .body(audioFlux);
    }
}
```

### Step 6 - 获取语音列表

```java
// VoiceController.java
package com.avatar3d.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class VoiceController {

    private static final String VOICES_URL =
        "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
        + "?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F0";

    private final WebClient webClient = WebClient.create();

    @GetMapping("/voices")
    public Mono<Map<String, Object>> listVoices() {
        return webClient.get()
            .uri(VOICES_URL)
            .retrieve()
            .bodyToMono(List.class)
            .map(voices -> Map.of(
                "current", "zh-CN-XiaoxiaoNeural",
                "voices", voices
            ));
    }
}
```

---

## 四、接口说明

### 4.1 TTS 合成

```
GET /api/tts?text=要合成的文本&voice=zh-CN-XiaoxiaoNeural&rate=%2B0%25&pitch=%2B0Hz
```

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| text | 是 | - | 要合成的文本 |
| voice | 否 | zh-CN-XiaoxiaoNeural | 语音名称 |
| rate | 否 | +0% | 语速，如 +10%、-5% |
| pitch | 否 | +0Hz | 音调 |

**响应：** `Content-Type: audio/mpeg`，流式返回 MP3 音频

---

### 4.2 语音列表

```
GET /api/tts/voices
```

**响应：**

```json
{
  "current": "zh-CN-XiaoxiaoNeural",
  "voices": [
    { "Name": "zh-CN-XiaoxiaoNeural", "ShortName": "zh-CN-XiaoxiaoNeural", "Gender": "Female", "Locale": "zh-CN" },
    { "Name": "zh-CN-YunxiNeural", "ShortName": "zh-CN-YunxiNeural", "Gender": "Male", "Locale": "zh-CN" }
  ]
}
```

---

## 五、中文语音推荐

| 语音 | 性别 | 特点 |
|------|------|------|
| zh-CN-XiaoxiaoNeural | 女 | 微软神经网络语音，自然度高，推荐 |
| zh-CN-YunxiNeural | 男 | 微软神经网络语音 |
| zh-CN-XiaoyiNeural | 女 | 年轻女性 |
| zh-CN-YunyangNeural | 男 | 新闻播报风格 |
| zh-CN-liaoningNeural | 女 | 东北口音 |
| zh-CN-shaanxiNeural | 男 | 陕西方言 |

---

## 六、常见问题

### Q: edge-tts 是否需要付费？
不需要。使用的是微软 Edge 浏览器的公开接口，`TRUSTED_CLIENT_TOKEN` 是硬编码的公开值，任何人可直接使用。

### Q: 调用频率有限制吗？
有。微软未公开明确限制，但建议每次合成文本不超过 300 字，长文本建议分段合成。

### Q: WebSocket 连接失败怎么办？
- 检查网络代理设置
- 确认防火墙未阻止 `wss://speech.platform.bing.com`
- 尝试设置更长的 `connect-timeout`

### Q: 如何处理长文本？
建议前端/业务层将长文本按标点符号切分成不超过 300 字的段落，分别调用 TTS，再拼接音频。
