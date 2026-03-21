# Change: AI平台添加语音ASR识别接口

## Why
ai-platform需要支持语音输入识别功能。用户上传音频文件后，系统需通过Silero VAD检测是否包含有效语音，然后调用SiliconFlow ASR服务将语音转为文字返回给前端。

## What Changes
- 新增REST API接口接收base64编码的WAV音频数据
- 集成Silero VAD进行语音活动检测
- 使用JDK 21 HTTP Client调用SiliconFlow ASR API
- 返回ASR识别结果给前端

## Impact
- 新增能力: ai-platform语音识别能力
- 受影响代码: ai-platform模块新增controller和service
