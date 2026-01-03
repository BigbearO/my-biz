# 设计：添加订单管理系统

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    表示层                                │
│                    (Controllers + VOs)                   │
│  OrderController, CommodityController, AccountController   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      业务层                              │
│                     (Services)                          │
│  OrderService, CommoditySkuService, GameAccountService  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   数据访问层                               │
│                  (Mappers + XML)                          │
│            MyBatis Plus + 自定义Mappers                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   数据存储层                               │
│                       MySQL 8.0                         │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  外部服务                                  │
│  闲鱼开放API, Nacos, RocketMQ (保留)               │
└─────────────────────────────────────────────────────────────┘
```

## 关键设计决策

### 1. 多租户架构

**决策**：在所有表中使用tenant_id字段进行自动过滤

**理由**：
- Pig框架需要多租户支持
- 确保租户之间的数据隔离
- 使用MyBatis Plus拦截器简化实现

**实现**：
```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor() {
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor();
        // 从安全上下文配置租户ID提取
        return interceptor;
    }
}
```

### 2. 时长计算算法

**决策**：会员时长计算采用三场景方法

**理由**：
- 提供最佳用户体验（最大化会员时间）
- 业务需求指定了这种行为
- 清晰且可维护的逻辑

**算法**：
```
IF 账号不存在:
    创建新账号
    expire_time = pay_time + days
    is_cumulative = 0
ELSE IF 账号存在 AND expire_time > NOW():
    // 账号在会员期间内
    expire_time = expire_time + days
    is_cumulative = 1
ELSE:
    // 账号存在但已过期
    expire_time = NOW() + days
    start_time = NOW()
    is_cumulative = 0
END IF
```

### 3. Excel导入策略

**决策**：使用流式读取，失败时事务回滚

**理由**：
- 高效处理大文件（1000+订单）
- 确保数据一致性
- 提供清晰的错误反馈

**实现**：
```java
@Transactional
public ImportResult importOrders(MultipartFile file) {
    List<OrderExcelDTO> data = ExcelUtils.readStreaming(file);
    for (OrderExcelDTO row : data) {
        try {
            // 处理行
            createOrder(row);
        } catch (Exception e) {
            // 记录错误，继续处理
            errors.add(new ImportError(rowNumber, e.getMessage()));
        }
    }
    return result;
}
```

**权衡**：
- ✅ 高效的内存使用
- ✅ 部分成功处理
- ⚠️ 无法为所有行使用单个事务（会在第一个错误时回滚）

### 4. 闲鱼同步策略

**决策**：通过order_no过滤现有订单，增量同步

**理由**：
- 避免重复数据
- 高效的增量同步
- 幂等操作

**实现**：
```java
public void syncOrders() {
    List<XianyuOrderDTO> xianyuOrders = xianyuApiClient.fetchOrders();
    
    for (XianyuOrderDTO xianyuOrder : xianyuOrders) {
        TradeOrder existOrder = orderMapper.getByOrderNo(xianyuOrder.getOrderNo());
        if (existOrder != null) {
            continue; // 跳过现有订单
        }
        // 处理新订单
        createOrder(xianyuOrder);
    }
}
```

### 5. 定时任务

**决策**：使用Spring的@Scheduled注解（而不是Quartz）

**理由**：
- 配置更简单
- 满足我们的需求（2个任务）
- 无外部依赖
- 与Spring生命周期更好地集成

**配置**：
```java
@Component
@EnableScheduling
public class XianyuSyncTask {
    @Scheduled(cron = "0 0 */1 * * ?") // 每小时
    public void syncXianyuOrders() {
        xianyuOrderSyncService.syncOrders();
    }
}
```

### 6. MQ集成（保留）

**决策**：接口保留但在v1.0中未主动使用

**理由**：
- 未来的可扩展性
- 当前需求不需要
- 避免复杂性开销

**实现**：
```java
@Component
public class GameTaskSender {
    // 未来MQ实现的占位符
    public void send(Integer gameType, List<GameAccount> accounts) {
        log.info("MQ接口保留: gameType={}, accounts={}", 
            gameType, accounts.size());
        // TODO: 实现实际的MQ发送
    }
}
```

## 数据模型

### 实体关系

```
GameTypeConfig (游戏类型配置)
    ↓ 1:N
CommoditySku (商品SKU) ──┐
    ↓ 1:N                │
TradeOrder (订单) ───────┼─── AccountDurationRecord (时长记录)
    ↓ 1:N                │
GameAccount (游戏账号) ───┘

PlatformConfig (平台配置)
    ↓ 1:N
TradeOrder (订单)

OrderSyncLog (同步日志)
```

### 关键索引

**性能关键索引**：
- `trade_order.order_no` (UNIQUE) - 订单查找、重复防护
- `trade_order(platform, user_account)` - 常见查询模式
- `trade_order.pay_time` - 基于时间的过滤
- `game_account(user_account, game_type)` (UNIQUE) - 账号查找
- `game_account.expire_time` - 过期扫描
- `commodity_sku(platform, commodity_id)` (UNIQUE) - SKU查找

## 安全考虑

### 1. 凭证加密
- 算法：AES-256
- 密钥管理：环境变量或Nacos
- 存储：在数据库中加密，按需解密

```java
public class GameAccountService {
    public void updateCredential(String accountNo, String credential) {
        String encrypted = EncryptionUtils.encrypt(credential);
        // 存储加密的凭证
    }
}
```

### 2. SQL注入防护
- 使用MyBatis Plus参数绑定（无SQL拼接）
- 验证用户输入
- 使用预处理语句

### 3. 多租户数据隔离
- 通过租户拦截器自动过滤
- 永远不允许跨租户数据访问
- 在所有操作上验证租户上下文

### 4. API安全
- Pig框架处理认证/授权
- 生产环境使用HTTPS
- 限流（未来）

## 错误处理策略

### 异常层次结构
```
Exception (根)
  └─ BusinessException (业务逻辑错误)
      ├─ OrderNotFoundException
      ├─ CommodityNotFoundException
      ├─ DuplicateOrderException
      └─ ValidationException
```

### 全局异常处理器
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("意外错误", e);
        return Result.failed(500, "系统错误");
    }
}
```

## 性能优化

### 1. 数据库优化
- 在频繁查询的字段上添加索引
- 使用连接池（HikariCP）
- 使用EXPLAIN优化慢查询

### 2. 缓存策略
- 缓存游戏类型（很少变化）
- 缓存SKU配置（不频繁更新）
- 如需要使用Redis（未来）

### 3. 批量操作
- Excel导入：每批处理100条
- 尽可能使用批量插入
- 避免N+1查询问题

### 4. 分页
- 所有列表查询必须分页
- 默认页面大小：20
- 最大页面大小：100

## 测试策略

### 1. 单元测试
- 专注于业务逻辑
- 模拟外部依赖（闲鱼API）
- 测试边缘情况：
  - 账号不存在
  - 账号在会员期间
  - 账号已过期
  - 重复订单
  - SKU缺失

### 2. 集成测试
- 端到端测试API端点
- 测试数据库事务
- 测试多租户隔离
- 测试定时任务

### 3. 性能测试
- 使用真实数据进行负载测试
- 监控响应时间
- 识别瓶颈

### 4. 安全测试
- 测试SQL注入
- 测试XSS防护
- 测试租户隔离
- 测试凭证加密

## 监控和日志

### 1. 日志级别
- **ERROR**：应用程序错误、异常
- **WARN**：业务异常、可恢复的错误
- **INFO**：重要的业务事件（订单导入、同步）
- **DEBUG**：详细执行流程（仅开发环境）

### 2. 要监控的关键指标
- 订单导入成功率
- 闲鱼同步成功率
- API响应时间
- 数据库查询性能
- 错误率

### 3. 告警
- 导入失败率 > 10%
- 同步连续失败 > 3次
- API响应时间 > 1秒
- 数据库连接池错误

## 部署考虑

### 1. 配置管理
- Nacos中特定环境的配置
- Nacos加密配置中的敏感数据（API密钥、密码）
- 无硬编码值

### 2. 数据库迁移
- 版本控制的SQL脚本
- Flyway或Liquibase（未来）
- 先在开发/预发布环境中测试迁移

### 3. 零停机部署
- 蓝绿部署（未来）
- 健康检查端点
- 优雅关闭

### 4. 回滚策略
- 保留上一版本可用
- 数据库向后兼容
- 功能标志（未来）

## 未来可扩展性

### 1. 附加平台
- 抽象平台API客户端接口
- 实现每个平台的客户端
- 添加特定平台的配置

### 2. 附加游戏类型
- 通过数据库可配置
- 无需代码更改
- 根据需要扩展枚举

### 3. MQ集成
- 接口已保留
- 需要时实现用于异步处理
- 添加消息生产者/消费者

### 4. 高级功能（未来）
- 订单审批工作流
- 自动通知
- 分析仪表板
- 移动应用支持

## 已知限制

### 当前限制
1. 无订单审批工作流（订单导入为"已支付"）
2. 无实际账号过期处理（仅筛选）
3. MQ接口保留但未使用
4. 仅支持闲鱼平台
5. 无前端（仅后端）

### 计划的改进
- 添加订单审批工作流
- 实现实际的过期处理
- 激活MQ进行异步处理
- 添加更多平台集成
- 开发前端UI

## 附录：示例代码

### 时长计算实现
```java
private void handleAccountDuration(TradeOrder order) {
    GameAccount account = accountMapper.getByUserAccountAndGameType(
        order.getUserAccount(), 
        order.getGameType()
    );
    
    LocalDateTime newExpireTime;
    boolean isCumulative = false;
    LocalDateTime beforeExpireTime = null;
    
    if (account == null) {
        // 场景1：账号不存在
        newExpireTime = order.getPayTime().plusDays(order.getDays());
        isCumulative = false;
        
        account = new GameAccount();
        account.setAccountNo(accountNoGenerator.generate());
        account.setUserAccount(order.getUserAccount());
        account.setGameType(order.getGameType());
        account.setStartTime(order.getPayTime());
        account.setExpireTime(newExpireTime);
        account.setTotalDays(order.getDays());
        accountMapper.insert(account);
        
    } else {
        beforeExpireTime = account.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        
        if (account.getExpireTime() != null && account.getExpireTime().isAfter(now)) {
            // 场景2：账号存在且在会员期间
            newExpireTime = account.getExpireTime().plusDays(order.getDays());
            isCumulative = true;
        } else {
            // 场景3：账号存在但已过期
            newExpireTime = now.plusDays(order.getDays());
            account.setStartTime(now);
            isCumulative = false;
        }
        
        account.setExpireTime(newExpireTime);
        account.setTotalDays(account.getTotalDays() + order.getDays());
        accountMapper.updateById(account);
    }
    
    // 记录时长变更
    AccountDurationRecord record = new AccountDurationRecord();
    record.setAccountNo(account.getAccountNo());
    record.setOrderNo(order.getOrderNo());
    record.setPlatform(order.getPlatform());
    record.setGameType(order.getGameType());
    record.setDays(order.getDays());
    record.setBeforeExpireTime(beforeExpireTime);
    record.setAfterExpireTime(newExpireTime);
    record.setIsCumulative(isCumulative ?1 : 0);
    durationRecordMapper.insert(record);
}
```

### Excel导入实现
```java
@Transactional
public ImportResult importOrders(MultipartFile file) {
    ImportResult result = new ImportResult();
    
    List<OrderExcelDTO> excelData = ExcelUtils.read(file, OrderExcelDTO.class);
    
    int success = 0;
    int fail = 0;
    List<ImportError> errors = new ArrayList<>();
    
    for (int i = 0; i < excelData.size(); i++) {
        OrderExcelDTO row = excelData.get(i);
        int rowNumber = i + 2;
        
        try {
            validateOrderData(row);
            
            CommoditySku sku = skuMapper.getByPlatformAndCommodityId(
                row.getPlatform(), 
                row.getCommodityId()
            );
            
            if (sku == null) {
                throw new BusinessException("商品不存在");
            }
            
            TradeOrder order = buildOrder(row, sku);
            orderMapper.insert(order);
            
            handleAccountDuration(order);
            
            success++;
        } catch (DuplicateKeyException e) {
            fail++;
            errors.add(new ImportError(rowNumber, "订单号重复"));
        } catch (Exception e) {
            fail++;
            errors.add(new ImportError(rowNumber, e.getMessage()));
        }
    }
    
    result.setSuccess(success);
    result.setFail(fail);
    result.setErrors(errors);
    
    return result;
}
```
