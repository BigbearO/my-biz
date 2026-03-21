## ADDED Requirements

### Requirement: 标准 WAV 文件解析
系统 SHALL 使用 `AudioSystem.getAudioInputStream()` 解析 WAV 文件，而非手动跳过固定字节数。

#### Scenario: 解析标准 WAV 文件
- **WHEN** 收到标准 16kHz 16bit WAV 文件
- **THEN** 正确提取 PCM 数据并归一化到 -1.0 ~ 1.0

#### Scenario: 解析不同位深 WAV 文件
- **WHEN** 收到非 16bit WAV 文件（如 8bit 或 24bit）
- **THEN** 根据 AudioFormat 进行正确的位深转换

### Requirement: VAD 语音活动检测
系统 SHALL 使用 Silero VAD 模型检测音频中是否包含有效语音。

#### Scenario: 检测到语音
- **WHEN** 音频中包含有效语音
- **THEN** 返回 true

#### Scenario: 未检测到语音
- **WHEN** 音频中无有效语音（如纯噪声）
- **THEN** 返回 false

### Requirement: WAV 文件上传接口
系统 SHALL 提供接收 WAV 文件的 HTTP 接口。

#### Scenario: 成功上传 WAV 文件
- **WHEN** 前端上传 WAV 文件
- **THEN** 返回 ASR 识别结果

#### Scenario: 文件为空
- **WHEN** 上传空文件
- **THEN** 返回错误信息

## MODIFIED Requirements

### Requirement: VoiceAsrController 接口
原有接口接收 base64 编码音频，新接口改为接收 MultipartFile 文件上传。

#### Scenario: 文件上传模式
- **WHEN** POST 请求携带 WAV 文件
- **THEN** 解码并执行 VAD 检测，检测通过后调用 ASR