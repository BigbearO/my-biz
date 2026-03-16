## ADDED Requirements

### Requirement: VoiceChat WebSocket语音对话
系统 SHALL 提供基于WebSocket的实时语音对话服务，支持前端语音输入→语音输出的完整流程。

#### Scenario: WebSocket连接建立
- **WHEN** 前端发起WebSocket连接，Header携带 `X-Agent-Id`
- **THEN** 系统验证Agent-ID有效性，返回Hello响应，包含session_id

#### Scenario: 语音输入
- **WHEN** 前端发送Opus编码的音频数据
- **THEN** 系统进行VAD检测，语音结束后进行ASR识别，返回识别结果

#### Scenario: 语音响应
- **WHEN** 大模型返回对话内容
- **THEN** 系统将文字按标点符号和数量切割，每段同时返回文字JSON和语音Opus数据，使用sequence保证顺序

#### Scenario: 敏感词拦截
- **WHEN** WebSocket握手或消息包含敏感词
- **THEN** 系统拒绝连接或返回错误消息

### Requirement: AgentService 智能体服务管理
系统 SHALL 根据Agent-ID路由到对应的智能体服务，支持服务缓存。

#### Scenario: 获取Agent服务
- **WHEN** 请求携带有效的Agent-ID
- **THEN** 系统从Caffeine缓存获取或创建对应的Agent服务实例

#### Scenario: Agent服务路由
- **WHEN** 多个请求携带不同Agent-ID
- **THEN** 系统路由到对应的Agent服务，每个Agent服务独立运行

### Requirement: AudioProcess 音频处理
系统 SHALL 提供完整的音频处理管道，包括Opus编解码和VAD检测。

#### Scenario: Opus解码
- **WHEN** 接收前端Opus音频数据
- **THEN** 系统解码为PCM格式，采样率16000Hz，单声道

#### Scenario: VAD语音活动检测
- **WHEN** 接收PCM音频数据
- **THEN** 系统使用Silero-VAD模型检测语音起止，返回语音片段

#### Scenario: TTS语音合成
- **WHEN** 大模型返回文字内容
- **THEN** 系统调用TTS服务合成语音，编码为Opus格式返回

### Requirement: ResponseManage 响应管理
系统 SHALL 管理文字和语音响应的同步，确保播放顺序正确。

#### Scenario: 文字分段
- **WHEN** 大模型返回完整文字
- **THEN** 系统按标点符号和文字数量切割成多个片段，每个片段带sequence序号

#### Scenario: 语音分段
- **WHEN** 文字分段完成
- **THEN** 系统对每段文字调用TTS，每段语音带对应sequence序号

#### Scenario: 流式响应
- **WHEN** 文字和语音处理完成
- **THEN** 系统立即通过WebSocket返回，不等待完整句子，支持流式输出

## MODIFIED Requirements

（无修改现有需求）

## REMOVED Requirements

（无删除需求）
