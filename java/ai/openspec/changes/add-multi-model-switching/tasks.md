## 1. 模型配置管理
- [ ] 1.1 创建 `ModelConfigEntity` 实体类
- [ ] 1.2 创建 `ModelConfigMapper` 数据访问层
- [ ] 1.3 创建 `ModelConfigManager` 管理器类
- [ ] 1.4 实现模型的增删改查 API

## 2. 服务改造
- [ ] 2.1 修改 `AiServiceContainer` 支持多模型初始化
- [ ] 2.2 修改 `ChatService` 支持动态模型选择
- [ ] 2.3 修改 `CustomerService` 支持动态模型选择
- [ ] 2.4 修改 `RAGService` 支持动态模型选择

## 3. 配置管理
- [ ] 3.1 在 `application-ai.yml` 添加模型配置
- [ ] 3.2 实现模型策略(成本优先、速度优先、质量优先)
- [ ] 3.3 添加模型切换日志

## 4. 测试
- [ ] 4.1 编写单元测试
- [ ] 4.2 集成测试验证模型切换
- [ ] 4.3 性能测试对比不同模型
