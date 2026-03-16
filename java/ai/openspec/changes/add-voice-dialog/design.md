# Design: AI语音对话服务

## Context

### 背景
- 用户需要通过网页前端实现实时语音对话
- 前端使用WebSocket + Opus音频传输
- 需要支持多个智能体(Agent)，每个Agent可配置不同的大模型和知识库
- 响应需要兼顾速度和完整性

### 参考实现
- proto.md: 小智设备通信协议(WebSocket + Opus)
- java-example: Silero VAD ONNX模型封装
- ai-platform现有代码: Agent管理

### 约束
- Java 21 + Spring Boot 3.x
- 复用java-example中的SileroVAD实现
- 兼容现有Agent管理体系

## Goals / Non-Goals

### Goals
- 实现完整的语音输入→语音输出对话流程
- 支持多个智能体，每个智能体独立服务
- 文字流式输出 + 语音流式输出
- 保证播放顺序正确

### Non-Goals
- RAG知识库关联(预留扩展)
- 意图识别(预留入口)
- 多轮对话记忆
- 视频/图片输入

## Decisions

### D1: WebSocket vs HTTP
- **Decision**: 使用WebSocket
- **Reason**: 实时语音需要双向流式通信，HTTP轮询延迟高
- **Alternatives**: SSE (仅适合文字流)

### D2: 消息格式
- **Decision**: 文本消息用JSON，二进制音频用Opus
- **Reason**: 参考proto.md实现，保持与现有前端兼容
- **Alternatives**: 全部用JSON(增加带宽)

### D3: 响应模式
- **Decision**: 文字和语音同时流式返回，使用sequence保证顺序
- **Reason**:
  - 纯文字：无法满足"语音响应速度"要求
  - 纯语音：无法满足"文字显示响应速度"要求
  - 同时流式：兼顾两者
- **Alternatives**:
  - 先文字后语音：延迟高
  - 仅语音：用户体验差

### D4: Agent服务缓存
- **Decision**: 使用Caffeine缓存Agent服务实例
- **Reason**: 语音对话低延迟要求，避免每次请求都创建服务
- **Alternatives**:
  - Spring Bean单例：无法支持动态配置
  - 每次创建：延迟高

### D5: VAD实现
- **Decision**: 复用java-example中SlieroVAD
- **Reason**: 已验证可用，减少重复开发
- **Alternatives**: 其他VAD库

### D6: 敏感词拦截时机
- **Decision**: WebSocket握手时+消息接收时双重拦截
- **Reason**: 握手阶段可快速过滤非法请求，消息阶段可拦截文字内容
- **Alternatives**: 仅消息时拦截(握手时已建立连接，浪费资源)

## Architecture

```
┌─────────────────┐     WebSocket      ┌─────────────────┐
│   前端网页       │─────────────────────│   后端服务       │
│                 │  JSON / Opus       │                 │
│  - 录音/播放     │                     │  - WebSocket    │
│  - Opus编解码    │                     │  - VAD检测      │
│  - 顺序控制      │                     │  - ASR          │
└─────────────────┘                     │  - LLM          │
                                        │  - TTS           │
                                        │  - Opus编码      │
                                        └─────────────────┘
                                                │
                                        ┌───────┴───────┐
                                        │  Agent路由    │
                                        │  (Caffeine)   │
                                        └───────────────┘
                                                │
                              ┌─────────────────┼─────────────────┐
                              │                 │                 │
                              ▼                 ▼                 ▼
                        ┌──────────┐     ┌──────────┐     ┌──────────┐
                        │ Agent A   │     │ Agent B  │     │ Agent C  │
                        │ (客服)    │     │ (闲聊)   │     │ (宠物医生)│
                        └──────────┘     └──────────┘     └──────────┘
```

## Data Flow

### 语音输入流程
```
1. 前端: 录音(PCM) → Opus编码 → WebSocket发送
2. 后端: WebSocket接收(Opus) → Opus解码 → PCM
3. 后端: VAD检测(SileroVAD) → 判断语音起止
4. 后端: 完整语音 → ASR识别 → 文字
5. 后端: 文字 → 意图识别(预留) → 大模型
```

### 响应输出流程
```
1. 大模型: 流式返回文字
2. 文字处理: 按标点符号和数量切割 → 添加sequence
3. 语音处理: 每段文字 → TTS → PCM → Opus编码 → 添加sequence
4. WebSocket: 同时发送文字JSON和音频二进制
5. 前端: 按sequence顺序播放文字和语音
```

## Sequence Diagram

```sequence
前端->>后端: 1. WebSocket连接(Header: X-Agent-Id)
后端->>前端: 2. Hello响应(session_id)

前端->>后端: 3. Opus音频数据(二进制)
后端->>后端: 4. Opus解码 → VAD检测
后端->>后端: 5. ASR识别

后端->>前端: 6. STT消息(识别结果)
后端->>后端: 7. 大模型对话
后端->>后端: 8. 文字分片 + TTS + Opus编码

后端->>前端: 9. LLM消息(文字流, sequence=1)
后端->>前端: 10. audio消息(语音流, sequence=1)
后端->>前端: 11. LLM消息(文字流, sequence=2)
后端->>前端: 12. audio消息(语音流, sequence=2)
...
后端->>前端: N. 消息(sequence=0, isFinal=true)
```

## Message Protocol

### 客户端→服务器

**Hello**
```json
{
  "type": "hello",
  "device_id": "xxx",
  "device_name": "Web测试设备",
  "device_mac": "xx:xx:xx:xx:xx:xx",
  "token": "xxx"
}
```

**Listen**
```json
{
  "type": "listen",
  "state": "detect",
  "text": "可选的文本输入"
}
```

**Abort**
```json
{
  "type": "abort",
  "session_id": "xxx",
  "reason": "wake_word_detected"
}
```

**音频**: 二进制Opus数据，发送空字节表示结束

### 服务器→客户端

**Hello响应**
```json
{
  "type": "hello",
  "session_id": "xxx"
}
```

**STT**
```json
{
  "type": "stt",
  "text": "识别到的文本"
}
```

**LLM**
```json
{
  "type": "llm",
  "text": "大模型回复内容",
  "emotion": "happy",
  "sequence": 1,
  "is_final": false
}
```

**TTS**
```json
{
  "type": "tts",
  "state": "sentence_start",
  "session_id": "xxx",
  "text": "要播放的文本",
  "sequence": 1
}
```

**Audio**: 二进制Opus数据

## Risks / Trade-offs

### R1: 延迟风险
- **Risk**: 端到端延迟过高影响体验
- **Mitigation**:
  - 文字和语音并行处理
  - Caffeine缓存Agent服务
  - 预连接TTS/ASR服务

### R2: 顺序错乱
- **Risk**: 并发情况下语音和文字顺序错乱
- **Mitigation**:
  - 使用sequence序号
  - 前端按sequence顺序播放

### R3: 资源占用
- **Risk**: VAD模型和WebSocket连接占用资源
- **Mitigation**:
  - 合理配置Caffeine缓存
  - 连接超时自动断开

### R4: 兼容性
- **Risk**: 不同前端实现音频格式不一致
- **Mitigation**:
  - 严格按照proto.md参数(16000Hz, 单声道, 960帧)

## Open Questions

- Q1: ASR/TTS服务提供商选择？(阿里云/腾讯云/自研)
- Q2: 是否需要支持WebSocket重连机制？
- Q3: 日志和监控如何接入现有系统？
- Q4: 是否需要支持WebSocket集群部署？
