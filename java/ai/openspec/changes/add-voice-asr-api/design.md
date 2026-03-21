## Context

在ai-platform模块中实现语音转文字功能，前端发送base64编码的WAV音频数据，系统先进行VAD检测确认包含有效语音，再调用第三方ASR服务转文字。

## Goals / Non-Goals

**Goals:**
- 提供HTTP API接收base64编码的WAV音频数据
- 使用Silero VAD检测音频是否包含语音
- 使用JDK 21 HTTP Client调用SiliconFlow ASR API
- 返回ASR识别结果给前端

**Non-Goals:**
- 流式语音识别(本期不做)
- WebSocket实时语音对话(已有add-voice-dialog变更)
- 本地ASR模型部署
- 多音频格式支持(仅支持WAV)

## Decisions

### Decision 1: 使用JDK 21 HTTP Client调用ASR API
- **原因**: 项目基于JDK 21，使用原生HTTP Client无需额外依赖
- **代码位置**: SiliconFlowAsrService使用java.net.http.HttpClient

### Decision 2: VAD检测使用Silero VAD ONNX模型
- **原因**: 与现有add-voice-dialog变更保持一致，复用Silero VAD实现
- **参考**: ai-graph模块已有SlieroVadDetector实现

### Decision 3: 接口为同步阻塞式
- **原因**: 单次ASR调用时间可控(通常<5秒)，同步实现更简单
- **替代方案**: 异步处理(增加复杂度，暂不需要)

### Decision 4: Base64解码处理
- **原因**: 接口接收base64字符串，需要解码为字节数组处理
- **方案**: 使用Base64.Decoder将音频数据解码为byte[]，内存中处理无需临时文件

## API设计

```
POST /api/voice/asr
Content-Type: application/json

Request:
  {
    "audioBase64": "base64编码的WAV音频数据",
    "sampleRate": 16000  // 可选，采样率，默认16000Hz
  }

Response:
  {
    "code": 200,
    "message": "success",
    "data": {
      "text": "识别到的文字内容",
      "hasSpeech": true  // VAD检测结果
    }
  }
```

## SiliconFlow ASR调用

```java
// 1. Base64解码
byte[] audioBytes = Base64.getDecoder().decode(audioBase64);

// 2. 保存为临时文件用于ASR调用
Path tempFile = Files.write(tempDir.resolve("audio.wav"), audioBytes);

// 3. 使用JDK 21 HTTP Client调用ASR
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.siliconflow.cn/v1/audio/transcriptions"))
    .header("Authorization", "Bearer " + apiKey)
    .POST(MultiPartBodyPublisher.of(
        "file", tempFile, "audio/wav",
        "model", "FunAudioLLM/SenseVoiceSmall"
    ))
    .build();
```

## 错误处理

| 场景 | 返回码 | 处理方式 |
|------|--------|----------|
| 无有效语音 | 400 | VAD检测未检测到语音，返回hasSpeech=false |
| ASR服务异常 | 502 | 上游服务错误，返回通用错误信息 |
| Base64解码失败 | 400 | 音频数据格式错误 |
| 音频格式不支持 | 400 | 仅支持WAV格式 |

## Open Questions

- [ ] 是否需要支持批量上传？(本期不做)
- [ ] 是否需要保存识别历史记录？(本期不做)
