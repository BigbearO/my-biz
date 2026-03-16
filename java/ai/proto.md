# 小智服务器测试页面通信协议分析

## 1. 概述

test_page.html 是小智 AI 设备的 Web 测试前端，通过 WebSocket 与后端服务器进行全双工通信，实现语音对话、实时音频传输等功能。

## 2. 连接流程

### 2.1 OTA 认证阶段

前端首先向 OTA 服务器发送 HTTP POST 请求进行设备认证：

```
POST /xiaozhi/ota/
Headers:
  Content-Type: application/json
  Device-Id: {deviceId}
  Client-Id: {clientId}
```

**请求 Body：**
```json
{
  "version": 0,
  "uuid": "",
  "application": {
    "name": "xiaozhi-web-test",
    "version": "1.0.0",
    "compile_time": "2025-04-16 10:00:00",
    "idf_version": "4.4.3",
    "elf_sha256": "..."
  },
  "ota": { "label": "xiaozhi-web-test" },
  "board": {
    "type": "Web测试设备",
    "ssid": "xiaozhi-web-test",
    "rssi": 0,
    "channel": 0,
    "ip": "192.168.1.1",
    "mac": "{deviceMac}"
  },
  "flash_size": 0,
  "minimum_free_heap_size": 0,
  "mac_address": "{deviceMac}",
  "chip_model_name": "",
  "chip_info": { "model": 0, "cores": 0, "revision": 0, "features": 0 },
  "partition_table": [{ "label": "", "type": 0, "subtype": 0, "address": 0, "size": 0 }]
}
```

**OTA 响应：**
```json
{
  "version": 0,
  "websocket": {
    "url": "ws://xxx:port/path",
    "token": "Bearer xxx"
  },
  ...
}
```

### 2.2 WebSocket 连接阶段

获取 OTA 响应中的 WebSocket URL 后，建立连接：

```
ws://host:port/path?authorization=Bearer xxx&device-id=xxx&client-id=xxx
```

### 2.3 Hello 握手阶段

连接成功后，发送 hello 握手消息：

```json
{
  "type": "hello",
  "device_id": "xxx",
  "device_name": "Web测试设备",
  "device_mac": "xx:xx:xx:xx:xx:xx",
  "token": "xxx",
  "features": {
    "mcp": true
  }
}
```

服务器响应：
```json
{
  "type": "hello",
  "session_id": "xxx",
  ...
}
```

## 3. 消息协议

### 3.1 消息格式

- **文本消息**：JSON 字符串
- **二进制消息**：Opus 编码的音频数据

### 3.2 消息类型

| 消息类型 | 方向 | 说明 |
|---------|------|------|
| `hello` | 客户端→服务器 | 握手消息 |
| `listen` | 客户端→服务器 | 发送文本消息/打断 |
| `abort` | 客户端→服务器 | 中断当前对话 |
| `tts` | 服务器→客户端 | 语音合成控制 |
| `stt` | 服务器→客户端 | 语音识别结果 |
| `llm` | 服务器→客户端 | 大模型回复 |
| `audio` | 服务器→客户端 | 音频控制消息 |
| `mcp` | 双向 | MCP 工具调用 |

### 3.3 消息详解

#### listen - 发送文本消息

```json
{
  "type": "listen",
  "state": "detect",
  "text": "用户输入的文本"
}
```

#### abort - 打断消息

```json
{
  "session_id": "xxx",
  "type": "abort",
  "reason": "wake_word_detected"
}
```

#### tts - 语音合成（服务器→客户端）

```json
{
  "type": "tts",
  "state": "start",        // start | sentence_start | sentence_end | stop
  "session_id": "xxx",
  "text": "要播放的文本"
}
```

#### stt - 语音识别结果

```json
{
  "type": "stt",
  "text": "识别到的文本"
}
```

#### llm - 大模型回复

```json
{
  "type": "llm",
  "text": "大模型回复内容",
  "emotion": "happy"  // 可选，情绪标签
}
```

#### mcp - MCP 工具调用

**服务器下发工具列表请求：**
```json
{
  "type": "mcp",
  "session_id": "xxx",
  "payload": {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }
}
```

**服务器调用工具：**
```json
{
  "type": "mcp",
  "session_id": "xxx",
  "payload": {
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "tool_name",
      "arguments": {}
    }
  }
}
```

**客户端响应：**
```json
{
  "session_id": "xxx",
  "type": "mcp",
  "payload": {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
      "tools": [...]
    }
  }
}
```

## 4. 音频传输协议

### 4.1 录音流程

1. **麦克风采集**：使用 `navigator.mediaDevices.getUserMedia` 获取音频流
2. **音频处理**：使用 AudioWorklet 或 ScriptProcessorNode 处理 PCM 数据
3. **Opus 编码**：使用 libopus 进行编码
4. **WebSocket 发送**：将编码后的 Opus 数据发送到服务器

**音频参数：**
- 采样率：16000 Hz
- 声道数：1 (单声道)
- 帧大小：960 样本
- 编码格式：Opus

**发送结束信号：** 发送空的 Uint8Array 表示录音结束

```javascript
// 发送结束信号
const emptyOpusFrame = new Uint8Array(0);
websocket.send(emptyOpusFrame);
```

### 4.2 音频播放流程

1. **WebSocket 接收**：接收服务器发送的 Opus 编码音频数据
2. **Opus 解码**：使用 libopus 解码
3. **音频播放**：通过 Web Audio API 播放

**播放机制：**
- 使用 BlockingQueue 阻塞队列缓冲音频包
- 预缓冲 6 个包后开始播放
- 支持流式解码和播放

## 5. 视觉分析协议

### 5.1 初始化

服务器通过 MCP 消息下发视觉分析配置：

```json
{
  "type": "mcp",
  "payload": {
    "method": "initialize",
    "params": {
      "capabilities": {
        "vision": {
          "url": "http://xxx/vision",
          "token": "xxx"
        }
      }
    }
  }
}
```

前端保存到 localStorage：
```javascript
localStorage.setItem('xz_tester_vision', JSON.stringify(visionConfig));
```

### 5.2 图像分析请求

```http
POST {visionUrl}
Headers:
  Device-Id: {deviceId}
  Client-Id: {clientId}
  Authorization: Bearer {token}

Body: multipart/form-data
  - question: "描述一下看到的物品"
  - image: <jpeg图片数据>
```

## 6. MCP 工具协议

### 6.1 工具定义格式

```json
{
  "name": "tool_name",
  "description": "工具描述",
  "parameters": {
    "type": "object",
    "properties": {
      "param_1": {
        "type": "string",
        "description": "参数描述"
      }
    },
    "required": ["param_1"]
  }
}
```

### 6.2 工具调用流程

1. 服务器发送 `tools/call` 请求
2. 客户端执行工具函数
3. 客户端返回执行结果

**响应格式：**
```json
{
  "session_id": "xxx",
  "type": "mcp",
  "payload": {
    "jsonrpc": "2.0",
    "id": 2,
    "result": {
      "content": [
        {
          "type": "text",
          "text": "{\"success\": true, \"data\": \"...\"}"
        }
      ],
      "isError": false
    }
  }
}
```

## 7. 配置管理

### 7.1 本地存储配置

| Key | 说明 |
|-----|------|
| `xz_tester_deviceMac` | 设备 MAC 地址 |
| `xz_tester_deviceName` | 设备名称 |
| `xz_tester_clientId` | 客户端 ID |
| `xz_tester_otaUrl` | OTA 服务器地址 |
| `xz_tester_vision` | 视觉分析配置 |

### 7.2 运行时配置

从页面表单获取：
- `deviceMac` - 设备 MAC 地址
- `deviceName` - 设备名称
- `clientId` - 客户端 ID（默认：web_test_client）
- `otaUrl` - OTA 服务器地址

## 8. 状态机

### 8.1 连接状态

```
离线 → (拨号) → 连接中 → (OTA+WebSocket) → 已连接
已连接 → (拨号/断开) → 离线
```

### 8.2 录音状态

```
未录音 → (点击录音按钮) → 录音中 → (再次点击/超时) → 未录音
```

### 8.3 对话状态

```
聆听中 → (录音结束) → 处理中 → (收到TTS) → 播放中 → 聆听中
```

## 9. 错误处理

### 9.1 连接错误

- OTA 请求失败：提示"无法从OTA服务器获取信息"
- WebSocket 连接失败：更新连接状态为"离线"
- Hello 响应超时：5秒超时，提示重试

### 9.2 音频错误

- 麦克风不可用：提示浏览器权限问题
- Opus 编码/解码失败：记录错误日志但继续运行

## 10. 文件结构

```
test/
├── test_page.html          # 主页面
├── js/
│   ├── app.js             # 应用入口
│   ├── config/
│   │   └── manager.js     # 配置管理
│   ├── core/
│   │   ├── audio/
│   │   │   ├── recorder.js    # 录音模块
│   │   │   ├── player.js     # 播放模块
│   │   │   ├── opus-codec.js # Opus编解码
│   │   │   └── stream-context.js # 流式上下文
│   │   ├── network/
│   │   │   ├── websocket.js  # WebSocket处理
│   │   │   └── ota-connector.js # OTA连接
│   │   └── mcp/
│   │       └── tools.js      # MCP工具管理
│   ├── ui/
│   │   └── controller.js     # UI控制器
│   └── utils/
│       ├── logger.js        # 日志
│       ├── blocking-queue.js # 阻塞队列
│       └── libopus.js       # Opus库(WASM)
└── css/
    └── test_page.css        # 样式
```

## 11. 总结

该前端采用以下技术栈实现与后端的通信：

1. **HTTP + WebSocket** 双通道通信
2. **Opus** 音频编解码，支持低带宽传输
3. **MCP 协议** 扩展工具能力
4. **Web Audio API** 实现录音和播放
5. **BlockingQueue** 实现音频流的缓冲播放
