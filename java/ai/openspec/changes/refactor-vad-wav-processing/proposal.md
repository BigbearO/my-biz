# Change: 重构 VAD 检测逻辑，实现标准 WAV 文件解析

## Why
当前 `VadService.detectSpeech` 存在以下问题：
1. 包含测试桩代码 `if(1==1) return App.ts();`，绕过了实际 VAD 逻辑
2. WAV 解析简单跳过 44 字节 header，未使用标准 `AudioSystem` API
3. 无法正确处理不同采样率、位深的 WAV 文件

## What Changes
1. **新增 VadUtils.java** - 抽取 App.java 中的 `readWavFileAsFloatArray` 和 `getSpeechTimestamps` 方法
2. **重构 VadService** - 使用标准 WAV 解析，移除测试桩代码
3. **改造 VoiceAsrController** - 接收 WAV 文件（`MultipartFile`）替代 base64 编码音频

## Impact
- Affected specs: ai-platform/VAD检测
- Affected code:
  - `VoiceAsrController.java` - 接口参数变更
  - `VadService.java` - 核心检测逻辑重构
  - `VadUtils.java` - 新增文件