# AI语音对话服务

## 项目概述

在 ai-platform 模块中实现一个实时语音对话服务，支持前端WebSocket连接，实现语音输入→VAD检测→ASR识别→大模型对话→TTS语音合成→OPUS编码→WebSocket返回的完整流程。

## 一、技术栈

- Spring Boot 3.x (Java 21)
- Spring   WebSocket 
- ONNX Runtime (使用Silero VAD)
- Caffeine (本地缓存)
- 音频处理：Opus编解码 (参考proto.md使用libopus)
- 消息序列化：Jackson + MsgPack

## 二、模块结构

### 2.1 WebSocket层

#### VoiceChatWebSocketHandler
- 处理WebSocket连接、消息收发
- 支持文本(JSON)和二进制(Opus)消息
- 路径: `/ws/voice-chat`

#### VoiceChatWebSocketConfig
- WebSocket配置类
- 注册WebSocket处理器和握手拦截器

#### VoiceChatHandshakeInterceptor
- WebSocket握手拦截器
- 从Header获取 `X-Agent-Id` 智能体ID
- 敏感词拦截

### 2.2 音频处理模块

#### AudioDataHandler
- 处理接收到的OPUS音频数据
- 音频参数(参考proto.md):
  - 采样率: 16000 Hz
  - 声道数: 1 (单声道)
  - 帧大小: 960 样本
  - 编码格式: Opus

#### VadProcessor
- VAD语音活动检测
- 参考java-example中SlieroVadDetector实现
- 配置:
  - startThreshold: 0.5
  - endThreshold: 0.2
  - minSilenceDurationMs: 500
  - speechPadMs: 400

#### AudioConverter
- 音频格式转换 (PCM/Opus互相转换)
- Opus解码用于接收前端音频
- Opus编码用于返回前端音频

### 2.3 AI服务层

#### AsrService
- 语音识别服务
- 调用第三方ASR接口
- 返回识别结果文本

#### LlmService
- 大模型对话服务
- 支持流式输出
- 文字按标点符号和数量切割

#### TtsService
- 语音合成服务
- 调用第三方TTS接口
- 返回PCM音频

#### IntentRecognitionService
- 意图识别服务
- 暂不做，留入口

### 2.4 智能体路由

#### AgentRouter
- 根据Agent-ID路由到对应的AI服务
- 从Header或QueryParam获取Agent-ID

#### AgentServiceCache
- 使用Caffeine缓存Agent服务实例
- 每个Agent可关联RAG知识库(暂不做)

### 2.5 响应管理

#### ResponseManager
- 管理文字/语音响应的顺序和同步
- 确保播放顺序正确

#### TextStreamHandler
- 处理文字流式输出

#### AudioStreamHandler
- 处理语音分段返回
- 预缓冲6个包后开始播放(参考proto.md)

## 三、核心流程设计

### 3.1 连接流程(参考proto.md)

#### Step 1: WebSocket连接
```
ws://host:port/ws/voice-chat?authorization=Bearer xxx&device-id=xxx&client-id=xxx
Header: X-Agent-Id: {agentId}
```

#### Step 2: Hello握手
客户端发送:
```json
{
  "type": "hello",
  "device_id": "xxx",
  "device_name": "Web测试设备",
  "device_mac": "xx:xx:xx:xx:xx:xx",
  "token": "xxx"
}
```

服务器响应:
```json
{
  "type": "hello",
  "session_id": "xxx"
}
```

#### Step 3: 录音与发送
- 前端点击录音按钮开始录音
- 使用AudioWorklet处理PCM数据
- Opus编码后通过WebSocket发送
- 发送空Uint8Array表示录音结束

### 3.2 请求处理流程

1. 前端建立WebSocket连接，Header携带 `X-Agent-Id`
2. 后端通过Agent-ID从Caffeine获取或创建对应的Agent服务
3. 前端发送Opus音频数据
4. 后端VAD检测(silero-vad)判断语音起止
5. 音频送ASR转文字
6. 文字经过意图识别入口(暂不做)→大模型
7. 大模型返回文字，按标点符号和数量切割
8. 文字部分: 直接WebSocket返回给前端展示
9. 语音部分: 调用TTS → Opus编码 → WebSocket返回
10. 确保播放顺序正确(文字序号 + 语音序号)

### 3.3 消息协议(参考proto.md)

#### 客户端→服务器消息

**listen - 发送语音/文本**
```json
{
  "type": "listen",
  "state": "detect",
  "text": "用户输入的文本(可选)"
}
```

**abort - 打断消息**
```json
{
  "session_id": "xxx",
  "type": "abort",
  "reason": "wake_word_detected"
}
```

**hello - 握手消息**
```json
{
  "type": "hello",
  "device_id": "xxx",
  "device_name": "Web测试设备",
  "device_mac": "xx:xx:xx:xx:xx:xx",
  "token": "xxx"
}
```

#### 服务器→客户端消息

**stt - 语音识别结果**
```json
{
  "type": "stt",
  "text": "识别到的文本"
}
```

**llm - 大模型回复**
```json
{
  "type": "llm",
  "text": "大模型回复内容",
  "emotion": "happy"
}
```

**tts - 语音合成控制**
```json
{
  "type": "tts",
  "state": "start",  // start | sentence_start | sentence_end | stop
  "session_id": "xxx",
  "text": "要播放的文本"
}
```

**audio - 音频数据(二进制Opus)**
- 直接发送Opus编码的二进制数据
- 预缓冲6个包后开始播放

## 四、关键类设计

### 4.1 消息体定义

#### VoiceChatMessage
```java
public class VoiceChatMessage {
    private String type;           // 消息类型: hello/listen/abort/stt/llm/tts/audio
    private String sessionId;     // 会话ID
    private String deviceId;      // 设备ID
    private String deviceName;    // 设备名称
    private String deviceMac;     // 设备MAC
    private String token;         // 认证令牌
    private String state;         // 状态: detect/start/sentence_start/sentence_end/stop
    private String text;          // 文本内容
    private String emotion;       // 情绪标签
    private String reason;        // 打断原因
    private byte[] audioData;     // 音频数据(二进制Opus)
}
```

#### AgentContext
```java
public class AgentContext {
    private String agentId;           // 智能体ID
    private String userId;            // 用户ID
    private LlmService llmService;    // LLM服务
    private ModelConfigBo modelConfig;// 模型配置
    private Map<String, Object> ext;  // 扩展配置
}
```

### 4.2 响应片段

#### ResponseChunk
```java
public class ResponseChunk {
    private String sessionId;         // 会话ID
    private String type;              // 消息类型: stt/llm/tts/audio
    private Integer sequence;         // 顺序号
    private String text;              // 文字内容
    private byte[] audioData;         // Opus音频数据
    private String state;             // 状态
    private boolean isFinal;          // 是否最后一段
}
```

## 五、关键实现要点

### 5.1 文字语音同步
- 大模型返回的文字按标点切割成多个片段
- 每个文字片段关联一个语音片段
- 使用 `sequence` 字段保证顺序
- 前端按sequence顺序播放
- 参考proto.md的BlockingQueue机制

### 5.2 流式响应
- 文字使用WebSocket流式返回
- 语音片段实时返回，不等完整句子
- 使用 `state` 标记: start/sentence_start/sentence_end/stop

### 5.3 敏感词拦截
- 新增 `SensitiveWordInterceptor`
- 在WebSocket握手时进行拦截
- 使用已有的敏感词库

### 5.4 Caffeine缓存
```java
Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterAccess(Duration.ofMinutes(30))
    .build();
```

### 5.5 音频处理(参考proto.md)
- 接收: WebSocket二进制 → Opus解码 → PCM → VAD → ASR
- 发送: LLM → TTS → PCM → Opus编码 → WebSocket二进制

### 5.6 状态机

**连接状态**
```
离线 → 连接中 → 已连接
已连接 → 断开 → 离线
```

**对话状态**
```
聆听中 → (录音结束) → 处理中 → (收到TTS) → 播放中 → 聆听中
```

## 六、文件清单

### 6.1 WebSocket层
- `trigger/websocket/VoiceChatWebSocketHandler.java`
- `trigger/websocket/VoiceChatWebSocketConfig.java`
- `trigger/websocket/VoiceChatHandshakeInterceptor.java`

### 6.2 消息定义
- `domain/vo/VoiceChatMessage.java`
- `domain/vo/AgentContext.java`
- `domain/vo/ResponseChunk.java`

### 6.3 音频处理
- `infra/processor/AudioDataHandler.java`
- `infra/processor/VadProcessor.java`
- `infra/processor/AudioConverter.java`

### 6.4 AI服务
- `infra/service/AsrService.java`
- `infra/service/LlmService.java`
- `infra/service/TtsService.java`
- `infra/service/IntentRecognitionService.java` (留入口)

### 6.5 路由与缓存
- `infra/router/AgentRouter.java`
- `infra/cache/AgentServiceCache.java`

### 6.6 响应管理
- `infra/manager/ResponseManager.java`
- `infra/handler/TextStreamHandler.java`
- `infra/handler/AudioStreamHandler.java`

## 七、配置项 (application.yml)

```yaml
voice:
  chat:
    websocket:
      path: /ws/voice-chat
      max-message-size: 10MB
    audio:
      sample-rate: 16000
      channels: 1
      frame-size: 960
    vad:
      model-path: classpath:models/silero_vad.onnx
      start-threshold: 0.5
      end-threshold: 0.2
      min-silence-ms: 500
      speech-pad-ms: 400
    asr:
      enabled: true
      provider: aliyun
    tts:
      enabled: true
      provider: aliyun
      voice: xiaoyun
    llm:
      provider: openai
    cache:
      agent-service:
        max-size: 1000
        expire-minutes: 30
    player:
      pre-buffer-count: 6
```

## 八、注意事项

1. WebSocket连接支持二进制(音频)和文本(JSON)消息
2. Opus解码使用libopus (参考proto.md)
3. TTS返回的PCM需要编码为Opus
4. 考虑并发场景下的顺序控制
5. 错误处理和重连机制
6. 资源释放(VAD模型、连接等)
7. 播放时使用BlockingQueue，预缓冲6个包(参考proto.md)
8. 发送空Uint8Array表示录音结束(参考proto.md)
9. 文字和语音需要流式返回，保证响应速度
10. 每个Agent可关联RAG知识库(暂不做，预留扩展)
