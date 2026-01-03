# Change: Add Multi-Model Switching Capability

## Why
当前系统固定使用单一 AI 模型,无法根据不同场景(如对话、RAG、媒体识别)选择最优模型。用户需要灵活切换模型的能力,以平衡成本、速度和效果。

## What Changes
- 添加模型配置管理功能,支持多个 AI 模型
- 实现动态模型选择策略
- 为不同服务(对话、客服、RAG、宠物医生)配置默认模型
- 添加模型性能监控和切换日志

## Impact
- **Affected specs**: chat-service, model-management
- **Affected code**:
  - `ai-biz/src/main/java/com/bigbear/ai/biz/manager/AiServiceContainer.java`
  - `ai-biz/src/main/java/com/bigbear/ai/biz/service/ChatService.java`
  - `ai-biz/src/main/java/com/bigbear/ai/biz/service/CustomerService.java`
  - 新增 Model 配置实体和管理器
- **BREAKING**: 修改服务初始化逻辑,需要配置默认模型
