## 1. 实现

- [x] 1.1 创建VoiceAsrController，提供POST /api/voice/asr接口接收JSON格式base64音频数据
- [x] 1.2 创建VadService，集成Silero VAD检测音频是否包含有效语音
- [x] 1.3 创建SiliconFlowAsrService，使用JDK 21 HTTP Client调用ASR接口
- [x] 1.4 实现Base64解码和WAV音频数据临时存储处理
- [x] 1.5 添加application.yml配置项(siliconflow api key, vad模型路径等)

## 2. 测试

- [ ] 2.1 编写VAD检测单元测试
- [ ] 2.2 编写ASR接口调用单元测试
- [ ] 2.3 接口集成测试

## 3. 文档

- [ ] 3.1 更新API文档
