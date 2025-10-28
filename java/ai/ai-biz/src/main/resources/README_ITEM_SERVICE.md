# 商品智能服务使用说明

## 功能介绍

本模块实现了基于langchain4j的商品智能服务，支持以下功能：

1. **商品管理**：SaaS用户可创建和编辑商品信息，包含定价策略提示词
2. **对话记忆**：基于PostgreSQL的对话记忆存储，支持tenant_id隔离和滑动窗口算法（最新10条）
3. **RAG增强对话**：结合商品信息向量和对话历史进行智能回复

## 数据库初始化

执行`db/init.sql`脚本创建所需的表结构：

```bash
psql -U username -d database_name -f db/init.sql
```

## API接口

### 1. 创建/更新商品

```http
POST /api/item
Content-Type: application/json
X-Tenant-Id: tenant123

{
  "id": 1,
  "tenantId": "tenant123",
  "itemDO": {
    "soldPrice": "999.00",
    "desc": "高性能笔记本电脑",
    "pricingStrategyPrompt": "基于市场行情动态调整价格，保持竞争力",
    "itemLabelExtList": [
      {
        "properties": "品牌:苹果##型号:MacBook Pro"
      }
    ]
  },
  "sellerDO": {
    "city": "上海",
    "signature": "专业数码销售"
  }
}
```

### 2. 获取商品信息

```http
GET /api/item?itemId=1
X-Tenant-Id: tenant123
```

### 3. 商品对话

```http
POST /api/item/chat?itemId=1&conversationId=conv001
Content-Type: application/json
X-Tenant-Id: tenant123

{
  "query": "这个笔记本电脑适合编程吗？"
}
```

## 配置说明

1. **Langchain4j配置**：在`application.yml`中配置语言模型和嵌入模型
2. **数据库配置**：确保PostgreSQL数据库连接正确
3. **Tenant隔离**：所有API都需要通过`X-Tenant-Id`头指定租户ID

## 核心组件

- `ItemService`：商品管理和向量化服务
- `ItemChatService`：基于RAG的对话服务
- `PgChatMemoryStore`：PostgreSQL对话记忆存储

## 注意事项

1. 确保langchain4j相关依赖已正确引入
2. 生产环境下建议替换内存缓存为持久化存储
3. 定期清理过期的对话记忆以优化数据库性能