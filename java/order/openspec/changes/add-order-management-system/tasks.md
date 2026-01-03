# 任务：添加订单管理系统

## 阶段1：数据库设置（1天）

### 1.1 创建数据库架构
- [ ] 使用SQL脚本创建7个数据库表：
  - [ ] `game_type_config`（游戏类型配置表）
  - [ ] `commodity_sku`（商品SKU配置表）
  - [ ] `platform_config`（平台配置表）
  - [ ] `trade_order`（订单表）
  - [ ] `game_account`（游戏账号表）
  - [ ] `account_duration_record`（账号时长记录表）
  - [ ] `order_sync_log`（订单同步日志表）
- [ ] 添加必要的索引：
  - [ ] `trade_order.order_no` 上的唯一索引
  - [ ] `commodity_sku.sku_id` 上的唯一索引
  - [ ] `commodity_sku(platform, commodity_id)` 上的唯一索引
  - [ ] `game_account.account_no` 上的唯一索引
  - [ ] 其他按规范要求的性能索引
- [ ] 插入初始数据：
  - [ ] 游戏类型：原神（0），星穹铁道（1）
  - [ ] 平台配置：启用同步的闲鱼

### 1.2 验证数据库设置
- [ ] 测试表创建
- [ ] 验证索引
- [ ] 测试外键（如果有）

## 阶段2：基础框架（2天）

### 2.1 创建实体类
- [ ] 创建实体包：`com.bigbear.order.entity`
- [ ] 创建7个实体类：
  - [ ] `TradeOrder.java`
  - [ ] `CommoditySku.java`
  - [ ] `GameAccount.java`
  - [ ] `PlatformConfig.java`
  - [ ] `GameTypeConfig.java`
  - [ ] `AccountDurationRecord.java`
  - [ ] `OrderSyncLog.java`
- [ ] 添加Lombok注解（@Data、@TableName等）
- [ ] 继承BaseEntity（id、createTime、updateTime、delFlag、tenantId）

### 2.2 创建Mapper接口和XML
- [ ] 创建mapper包：`com.bigbear.order.mapper`
- [ ] 创建7个继承BaseMapper的Mapper接口：
  - [ ] `TradeOrderMapper.java`
  - [ ] `CommoditySkuMapper.java`
  - [ ] `GameAccountMapper.java`
  - [ ] `PlatformConfigMapper.java`
  - [ ] `GameTypeConfigMapper.java`
  - [ ] `AccountDurationRecordMapper.java`
  - [ ] `OrderSyncLogMapper.java`
- [ ] 添加自定义查询方法：
  - [ ] `getByOrderNo(String orderNo)` - OrderMapper
  - [ ] `getByPlatformAndCommodityId(String platform, String commodityId)` - CommoditySkuMapper
  - [ ] `getByUserAccountAndGameType(String userAccount, Integer gameType)` - GameAccountMapper
  - [ ] `getByPlatform(String platform)` - PlatformConfigMapper
  - [ ] `getExpiringAccounts(LocalDateTime expireTime)` - GameAccountMapper
- [ ] 在 `resources/mapper/` 中创建对应的XML文件
- [ ] 配置MyBatis Plus（mapper-locations、type-aliases-package）

### 2.3 创建DTO和VO类
- [ ] 在order-api中创建DTO包：`com.bigbear.order.api.dto`
- [ ] 在order-api中创建VO包：`com.bigbear.order.api.vo`
- [ ] 创建DTO：
  - [ ] `OrderExcelDTO.java`（Excel导入）
  - [ ] `CommodityCreateDTO.java`
  - [ ] `AccountUpdateDTO.java`
- [ ] 创建VO：
  - [ ] `OrderVO.java`
  - [ ] `GameAccountVO.java`
  - [ ] `CommoditySkuVO.java`
  - [ ] `PlatformConfigVO.java`
  - [ ] `GameTypeVO.java`
  - [ ] `DurationRecordVO.java`
  - [ ] `SyncLogVO.java`
- [ ] 创建响应类：
  - [ ] `Result.java`（统一响应）
  - [ ] `PageResult.java`（分页响应）
  - [ ] `ImportResult.java`（导入结果）

### 2.4 创建枚举类
- [ ] 创建枚举包：`com.bigbear.order.api.enums`
- [ ] 创建 `GameTypeEnum.java`（0=GENSHIN，1=HONKAI_STAR_RAIL）
- [ ] 创建 `OrderStatusEnum.java`（1=PAID，2=COMPLETED，3=CANCELLED）
- [ ] 创建 `SyncTypeEnum.java`（AUTO，MANUAL）

## 阶段3：业务逻辑（4天）

### 3.1 商品SKU管理
- [ ] 创建 `CommoditySkuService` 接口
- [ ] 创建 `CommoditySkuServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `create(CommodityCreateDTO dto)` - 创建SKU
  - [ ] `listByPage(CommodityQuery query)` - 分页列出SKU
  - [ ] `update(String skuId, CommodityUpdateDTO dto)` - 更新SKU
  - [ ] `delete(String skuId)` - 逻辑删除
  - [ ] `getByPlatformAndCommodityId(String platform, String commodityId)` - 根据平台和商品获取
- [ ] 添加事务支持（@Transactional）
- [ ] 添加验证和错误处理

### 3.2 订单管理
- [ ] 创建 `OrderService` 接口
- [ ] 创建 `OrderServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `importOrders(MultipartFile file)` - Excel导入
  - [ ] `listByPage(OrderQuery query)` - 分页列出订单
  - [ ] `getByOrderNo(String orderNo)` - 获取订单详情
  - [ ] `cancelOrder(String orderNo)` - 取消订单
- [ ] 实现Excel导入逻辑：
  - [ ] 解析Excel文件
  - [ ] 验证每一行
  - [ ] 查询SKU配置
  - [ ] 创建订单
  - [ ] 调用handleAccountDuration()
  - [ ] 记录结果（成功/失败/错误）
- [ ] 添加事务支持
- [ ] 实现重复订单的错误处理

### 3.3 游戏账号管理
- [ ] 创建 `GameAccountService` 接口
- [ ] 创建 `GameAccountServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `listByPage(AccountQuery query)` - 分页列出账号
  - [ ] `getByAccountNo(String accountNo)` - 获取账号详情
  - [ ] `updateCredential(String accountNo, String credential)` - 更新凭证
  - [ ] `getExpiringAccounts(LocalDateTime expireTime)` - 获取即将过期的账号
  - [ ] `getDurationRecords(String accountNo, PageParam pageParam)` - 获取时长记录
- [ ] 实现凭证加密
- [ ] 添加多租户过滤

### 3.4 时长计算逻辑
- [ ] 在OrderService中实现 `handleAccountDuration(TradeOrder order)` 方法：
  - [ ] 根据userAccount和gameType查询账号
  - [ ] 如果账号不存在：
    - [ ] 创建新账号
    - [ ] 设置startTime = payTime
    - [ ] 设置expireTime = payTime + days
    - [ ] 设置isCumulative = 0
  - [ ] 如果账号存在且expireTime > now：
    - [ ] 新的expireTime = expireTime + days
    - [ ] 设置isCumulative = 1
  - [ ] 如果账号存在且expireTime <= now：
    - [ ] 新的expireTime = now() + days
    - [ ] 设置startTime = now()
    - [ ] 设置isCumulative = 0
  - [ ] 更新账号的totalDays
  - [ ] 在account_duration_record中记录时长变更
- [ ] 添加全面的单元测试
- [ ] 测试所有三个场景

### 3.5 时长记录服务
- [ ] 创建 `DurationRecordService` 接口
- [ ] 创建 `DurationRecordServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `recordDurationChange(DurationRecordDTO dto)` - 记录时长变更
  - [ ] `listByAccount(String accountNo, PageParam pageParam)` - 按账号列出记录
  - [ ] `listByOrder(String orderNo)` - 按订单列出记录

## 阶段4：外部集成（2天）

### 4.1 闲鱼API客户端
- [ ] 创建 `XianyuApiClient` 类
- [ ] 实现认证（AppKey + AppSecret）
- [ ] 实现 `fetchOrders(PlatformConfig config)` 方法：
  - [ ] 调用闲鱼开放API
  - [ ] 将响应解析为 `XianyuOrderDTO` 列表
  - [ ] 处理错误和重试
  - [ ] 添加日志
- [ ] 创建 `XianyuOrderDTO` 数据传输对象

### 4.2 闲鱼订单同步服务
- [ ] 创建 `XianyuOrderSyncService` 接口
- [ ] 创建 `XianyuOrderSyncServiceImpl` 实现
- [ ] 实现 `syncOrders()` 方法：
  - [ ] 检查是否启用同步
  - [ ] 从闲鱼API获取订单
  - [ ] 过滤现有订单
  - [ ] 为每个订单查询SKU
  - [ ] 创建订单并处理时长
  - [ ] 记录同步日志
  - [ ] 优雅地处理错误
- [ ] 添加事务支持

### 4.3 同步日志服务
- [ ] 创建 `OrderSyncLogService` 接口
- [ ] 创建 `OrderSyncLogServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `createSyncLog(OrderSyncLog log)` - 创建同步日志
  - [ ] `listByPage(SyncLogQuery query)` - 列出同步日志
  - [ ] `updateSyncLogStatus(Long id, Integer status, String errorMsg)` - 更新状态

### 4.4 定时任务
- [ ] 创建 `XianyuSyncTask` 类：
  - [ ] 添加 @EnableScheduling
  - [ ] 添加 @Scheduled(cron = "0 0 */1 * * ?") 用于每小时同步
  - [ ] 实现syncXianyuOrders()方法
  - [ ] 添加日志
- [ ] 创建 `AccountExpirationTask` 类：
  - [ ] 添加 @EnableScheduling
  - [ ] 添加 @Scheduled(cron = "0 0 2 * * ?") 用于每日扫描
  - [ ] 实现scanExpiringAccounts()方法：
    - [ ] 查询7天内即将过期的账号
    - [ ] 按游戏类型分组
    - [ ] 发送到MQ（保留，目前仅记录日志）
  - [ ] 添加日志

## 阶段5：平台和游戏类型管理（1天）

### 5.1 平台配置管理
- [ ] 创建 `PlatformConfigService` 接口
- [ ] 创建 `PlatformConfigServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `listAll()` - 列出所有平台配置
  - [ ] `updatePlatform(String platform, PlatformUpdateDTO dto)` - 更新配置
  - [ ] `getByPlatform(String platform)` - 根据平台获取
- [ ] 在响应中实现凭证脱敏

### 5.2 游戏类型管理
- [ ] 创建 `GameTypeService` 接口
- [ ] 创建 `GameTypeServiceImpl` 实现
- [ ] 实现方法：
  - [ ] `listAll()` - 列出所有游戏类型
  - [ ] `getByGameType(Integer gameType)` - 根据类型获取

## 阶段6：控制器（2天）

### 6.1 订单控制器
- [ ] 创建 `OrderController` 类
- [ ] 实现端点：
  - [ ] `POST /api/v1/orders/import` - Excel导入
  - [ ] `GET /api/v1/orders` - 列出订单（带分页）
  - [ ] `GET /api/v1/orders/{orderNo}` - 获取订单详情
  - [ ] `PUT /api/v1/orders/{orderNo}/cancel` - 取消订单
- [ ] 为请求验证添加 @Valid
- [ ] 返回统一的Result格式
- [ ] 添加Swagger注解

### 6.2 商品控制器
- [ ] 创建 `CommodityController` 类
- [ ] 实现端点：
  - [ ] `POST /api/v1/commodities` - 创建SKU
  - [ ] `GET /api/v1/commodities` - 列出SKU（带分页）
  - [ ] `PUT /api/v1/commodities/{skuId}` - 更新SKU
  - [ ] `DELETE /api/v1/commodities/{skuId}` - 删除SKU

### 6.3 游戏账号控制器
- [ ] 创建 `GameAccountController` 类
- [ ] 实现端点：
  - [ ] `GET /api/v1/accounts` - 列出账号（带分页）
  - [ ] `GET /api/v1/accounts/{accountNo}` - 获取账号详情
  - [ ] `PUT /api/v1/accounts/{accountNo}/credential` - 更新凭证
  - [ ] `GET /api/v1/accounts/expiring` - 获取即将过期的账号
  - [ ] `GET /api/v1/accounts/{accountNo}/duration-records` - 获取时长记录

### 6.4 平台控制器
- [ ] 创建 `PlatformController` 类
- [ ] 实现端点：
  - [ ] `GET /api/v1/platforms` - 列出平台
  - [ ] `PUT /api/v1/platforms/{platform}` - 更新平台配置

### 6.5 游戏类型控制器
- [ ] 创建 `GameTypeController` 类
- [ ] 实现端点：
  - [ ] `GET /api/v1/game-types` - 列出游戏类型

### 6.6 订单同步控制器
- [ ] 创建 `OrderSyncController` 类
- [ ] 实现端点：
  - [ ] `POST /api/v1/sync/xianyu` - 立即触发同步
  - [ ] `GET /api/v1/sync/logs` - 列出同步日志（带分页）

### 6.7 全局异常处理器
- [ ] 使用@ControllerAdvice创建 `GlobalExceptionHandler` 类
- [ ] 处理常见异常：
  - [ ] BusinessException
  - [ ] DuplicateKeyException
  - [ ] IllegalArgumentException
  - [ ] ValidationException
  - [ ] Exception（捕获所有）
- [ ] 返回统一的错误格式

## 阶段7：工具类（1天）

### 7.1 Excel工具类
- [ ] 创建 `ExcelUtils` 类
- [ ] 实现 `read(MultipartFile file, Class<T> clazz)` 方法：
  - [ ] 使用EasyExcel或Apache POI
  - [ ] 支持大文件流式读取
  - [ ] 将Excel列映射到对象属性
  - [ ] 处理错误和日志

### 7.2 账号编号生成器
- [ ] 创建 `AccountNoGenerator` 类
- [ ] 实现 `generate()` 方法：
  - [ ] 生成唯一的账号编号
  - [ ] 格式：ACC + 时间戳 + 随机数

### 7.3 日期时间工具类
- [ ] 创建 `DateTimeUtils` 类
- [ ] 添加常用的日期/时间工具方法

### 7.4 加密工具类
- [ ] 创建 `EncryptionUtils` 类
- [ ] 实现凭证的AES加密
- [ ] 实现AES解密

## 阶段8：配置（1天）

### 8.1 应用配置
- [ ] 更新 `application.yml`：
  - [ ] 添加数据源配置
  - [ ] 添加MyBatis Plus配置
  - [ ] 添加Nacos配置
  - [ ] 添加RocketMQ配置（保留）
  - [ ] 添加闲鱼API配置
- [ ] 为开发创建 `application-dev.yml`
- [ ] 为生产创建 `application-prod.yml`

### 8.2 MyBatis Plus配置
- [ ] 创建 `MybatisPlusConfig` 类：
  - [ ] 配置分页
  - [ ] 配置逻辑删除
  - [ ] 配置租户拦截器
  - [ ] 配置自动填充（createTime、updateTime）

### 8.3 租户拦截器
- [ ] 创建 `TenantInterceptor` 类
- [ ] 实现自动的tenant_id过滤
- [ ] 在MybatisPlusConfig中配置

### 8.4 Swagger配置
- [ ] 配置API文档
- [ ] 添加项目信息
- [ ] 添加安全配置（如果需要）

## 阶段9：测试（3天）

### 9.1 单元测试
- [ ] 为OrderService编写单元测试
- [ ] 为CommoditySkuService编写单元测试
- [ ] 为GameAccountService编写单元测试
- [ ] 为DurationRecordService编写单元测试
- [ ] 为XianyuOrderSyncService编写单元测试
- [ ] 为时长计算逻辑编写单元测试（所有3个场景）
- [ ] 目标覆盖率：> 80%

### 9.2 集成测试
- [ ] 为OrderController编写集成测试
- [ ] 为CommodityController编写集成测试
- [ ] 为GameAccountController编写集成测试
- [ ] 为闲鱼同步编写集成测试
- [ ] 测试数据库事务
- [ ] 测试多租户隔离

### 9.3 性能测试
- [ ] 测试1000个订单的Excel导入（< 30秒）
- [ ] 测试API响应时间（列表查询 < 500毫秒）
- [ ] 测试并发请求（50并发）
- [ ] 识别并修复瓶颈

### 9.4 安全测试
- [ ] 测试SQL注入防护
- [ ] 测试多租户数据隔离
- [ ] 测试凭证加密/解密
- [ ] 测试API认证/授权

## 阶段10：文档和部署（2天）

### 10.1 文档
- [ ] 更新README.md
- [ ] 记录API端点
- [ ] 记录数据库架构
- [ ] 记录配置
- [ ] 添加部署指南
- [ ] 添加故障排除指南

### 10.2 构建和打包
- [ ] 测试Maven构建
- [ ] 验证Dockerfile
- [ ] 测试打包

### 10.3 部署
- [ ] 准备生产环境
- [ ] 配置Nacos
- [ ] 部署应用程序
- [ ] 运行冒烟测试
- [ ] 监控应用程序健康状况

### 10.4 最终验证
- [ ] 验证所有功能正常工作
- [ ] 验证定时任务执行
- [ ] 验证监控和日志
- [ ] 获得利益相关者的验收

## 里程碑

- **M1（第3天）**：数据库设置完成，基础框架就绪
- **M2（第7天）**：核心业务逻辑完成（订单、SKU、账号管理）
- **M3（第9天）**：闲鱼同步完成，定时任务已配置
- **M4（第12天）**：所有开发完成，所有测试通过
- **M5（第14天）**：部署到生产，验收测试通过

## 注意事项
- 所有任务应按顺序完成，除非另有说明
- 每个任务应在完全完成并测试后勾选
- 如果任务被阻塞，请在任务描述中记录阻塞因素
- 根据需要在实施过程中更新此检查清单
