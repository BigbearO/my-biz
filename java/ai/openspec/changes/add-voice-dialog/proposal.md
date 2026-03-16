# Change: AI语音对话服务

## Why

当前ai-platform模块仅支持智能体(Agent)的基础CRUD管理，缺少实时语音对话能力。用户需要通过WebSocket实现前端语音输入→实时语音响应的完整对话流程，包括：

1. 前端录音、Opus编码、WebSocket传输
2. 后端VAD语音活动检测
3. ASR语音识别
4. 大模型对话
5. TTS语音合成、Opus编码、WebSocket返回

## What Changes

在ai-platform模块新增语音对话服务，实现以下能力：

### 1. WebSocket通信层
- 新增 `/ws/voice-chat` WebSocket端点
- 支持Hello握手、文本消息(JSON)、二进制音频(Opus)
- Header携带 `X-Agent-Id` 路由到对应智能体服务

### 2. 音频处理管道
- Opus解码：接收前端Opus音频
- VAD检测：使用Silero-VAD模型检测语音起止(参考java-example)
- ASR识别：调用第三方ASR服务
- TTS合成：调用第三方TTS服务
- Opus编码：返回音频编码为Opus

### 3. 智能体路由
- 根据Agent-ID从Caffeine缓存获取/创建Agent服务
- 每个Agent可关联RAG知识库(暂不做，预留扩展)

### 4. 响应管理
- 文字按标点符号和数量切割
- 文字流式输出 + 语音流式输出
- 使用sequence保证播放顺序正确
- 兼容文字响应速度和语句完整性

### 5. 安全控制
- 敏感词拦截(请求拦截器)
- 意图识别入口(暂不做)

## Impact

- Affected specs: ai-platform (新增)
- Affected code: ai-platform模块
- 依赖: java-example中的SileroVAD实现
- 新增依赖: Opus编解码库、ONNX Runtime

## Out of Scope

- RAG知识库关联(暂不做)
- 意图识别(暂不做，留入口)
- 视频/图片输入(暂不做)
- 多轮对话记忆(暂不做)
