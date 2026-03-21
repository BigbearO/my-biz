## ADDED Requirements

### Requirement: 语音识别接口
系统SHALL提供REST API接收base64编码的WAV音频数据并返回ASR识别结果。

#### Scenario: 成功识别语音
- **GIVEN** 前端发送包含有效语音的base64编码WAV音频数据
- **WHEN** 调用POST /api/voice/asr接口
- **THEN** 系统解码base64数据并使用Silero VAD检测到语音
- **AND** 调用SiliconFlow ASR服务获取文字
- **AND** 返回识别文字和hasSpeech=true

#### Scenario: 无有效语音
- **GIVEN** 前端发送静音或噪音的base64编码WAV音频数据
- **WHEN** 调用POST /api/voice/asr接口
- **THEN** Silero VAD未检测到语音
- **AND** 返回hasSpeech=false，text为空

#### Scenario: ASR服务调用失败
- **GIVEN** SiliconFlow ASR服务不可用
- **WHEN** 前端发送有效音频数据
- **THEN** 系统返回502错误码和错误信息

### Requirement: VAD语音检测
系统SHALL使用Silero VAD模型检测音频是否包含有效语音。

#### Scenario: 检测到语音活动
- **GIVEN** 音频文件包含人声
- **WHEN** VadProcessor处理音频
- **THEN** 返回语音检测结果为true

### Requirement: ASR服务调用
系统SHALL使用JDK 21 HTTP Client调用SiliconFlow ASR API。

#### Scenario: 成功调用ASR
- **GIVEN** 配置有效的SiliconFlow API Key
- **WHEN** 发送音频文件到https://api.siliconflow.cn/v1/audio/transcriptions
- **THEN** 返回识别结果文本
- **AND** 使用FunAudioLLM/SenseVoiceSmall模型

### Requirement: Base64音频数据接收
系统SHALL支持接收JSON格式的base64编码WAV音频数据。

#### Scenario: 接收Base64音频数据
- **GIVEN** 前端通过JSON发送base64编码的WAV音频数据
- **WHEN** 音频格式为WAV
- **THEN** 系统解码base64为byte数组
- **AND** 处理完成后释放内存

#### Scenario: Base64解码失败
- **GIVEN** 前端发送无效的base64字符串
- **WHEN** 调用POST /api/voice/asr接口
- **THEN** 系统返回400错误码和"音频数据格式错误"信息
