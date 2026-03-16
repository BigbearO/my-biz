# Tasks: AI语音对话服务

## 1. 项目准备

- [x] 1.1 在ai-platform/pom.xml添加依赖(ONNX Runtime、Opus编解码、Caffeine)
- [x] 1.2 复制java-example中的SileroVAD相关类到ai-platform
- [x] 1.3 创建application-voice.yml配置模板

## 2. WebSocket层

- [x] 2.1 创建VoiceChatWebSocketConfig WebSocket配置
- [x] 2.2 创建VoiceChatWebSocketHandler 处理器
- [x] 2.3 创建VoiceChatHandshakeInterceptor 握手拦截器
- [x] 2.4 实现Hello握手逻辑
- [x] 2.5 实现二进制音频消息接收

## 3. 消息定义

- [x] 3.1 创建VoiceChatMessage 消息体
- [x] 3.2 创建AgentContext 智能体上下文
- [x] 3.3 创建ResponseChunk 响应片段

## 4. 音频处理

- [x] 4.1 创建AudioDataHandler 音频数据处理
- [x] 4.2 创建VadProcessor VAD处理(封装SileroVAD)
- [x] 4.3 创建AudioConverter 音频格式转换(Opus编解码) - 集成在AudioDataHandler中

## 5. AI服务

- [x] 5.1 创建AsrService 语音识别服务接口
- [x] 5.2 创建LlmService 大模型对话服务
- [x] 5.3 创建TtsService 语音合成服务接口
- [x] 5.4 创建IntentRecognitionService 意图识别(留入口)

## 6. 智能体路由

- [x] 6.1 创建AgentRouter 智能体路由
- [x] 6.2 创建AgentServiceCache Caffeine缓存
- [x] 6.3 实现Agent-ID到服务的映射

## 7. 响应管理

- [x] 7.1 创建ResponseManager 响应管理器
- [x] 7.2 实现文字分段逻辑(按标点符号和数量)
- [x] 7.3 实现语音分段逻辑
- [x] 7.4 实现sequence顺序控制

## 8. 安全控制

- [x] 8.1 创建SensitiveWordInterceptor 敏感词拦截器
- [x] 8.2 集成现有敏感词库
- [x] 8.3 WebSocket握手时敏感词检查

## 9. 测试

- [ ] 9.1 单元测试: VAD处理
- [ ] 9.2 单元测试: 文字分段
- [ ] 9.3 集成测试: WebSocket连接
- [ ] 9.4 集成测试: 完整语音对话流程

## 10. 文档

- [ ] 10.1 更新README说明
- [ ] 10.2 生成API文档
