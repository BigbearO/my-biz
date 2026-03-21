## Context

VAD（Voice Activity Detection）模块当前存在问题：
- 测试代码绕过实际逻辑
- WAV 解析方式不标准
- 需要支持标准的 WAV 文件上传接口

## Goals / Non-Goals

- Goals:
  - 实现标准 WAV 文件解析（使用 AudioSystem API）
  - 正确调用 Silero VAD 进行语音检测
  - 提供文件上传接口

- Non-Goals:
  - 不重构 App.java（保持其独立运行）
  - 不改变现有的 VAD 模型调用逻辑（只改 WAV 解析层）

## Decisions

1. **WAV 解析方式**: 使用 `AudioSystem.getAudioInputStream()` 替代手动跳过 header
2. **公共工具类**: 新增 `VadUtils.java` 复用 App.java 中的方法
3. **接口形式**: 改用 `MultipartFile` 接收 WAV 文件

## Risks / Trade-offs

- Risk: `AudioSystem` 可能无法解析所有 WAV 变体
- Mitigation: 先处理标准 WAV 格式，后续按需扩展

## Open Questions

- 是否需要保留 base64 编码的接口？