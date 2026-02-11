# OpenSpec 变更提案: 订单Excel导入功能重构

## 1. 概述

### 1.1 变更目标
- 重构 OrderController.importByExcel 接口
- 实现订单导入时自动处理游戏账号逻辑
- 支持账号存在时的会员时间累加或重置逻辑
- 确保订单和账号操作在同一事务中

### 1.2 涉及模块
- `mall-biz`: OrderController、OrderApplicationService、新增DTO

---

## 2. Excel模板格式

### 2.1 导入字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 账号 | String | 是 | 游戏账号（唯一标识）|
| 游戏类型 | Integer | 是 | 0=原神, 1=尘白禁区 |
| 会员天数 | Integer | 是 | 会员有效期天数 |

### 2.2 示例

| 账号 | 游戏类型 | 会员天数 |
|------|---------|---------|
| user@example.com | 0 | 30 |
| game_account_001 | 1 | 60 |

---

## 3. 核心业务逻辑

### 3.1 账号处理规则

```
导入订单
    ↓
根据"账号"查询是否已存在
    ↓
【场景1: 账号不存在】
    → 新增账号
    → startTime = 今天
    → expireTime = 今天 + 会员天数
    
【场景2: 账号存在】
    ↓
    判断 expireTime >= 今天?
    ↓
    【场景2a: expireTime >= 今天】（账号仍在有效期内）
        → 更新账号
        → startTime = 保持不变（不更新）
        → expireTime = 今天 + 会员天数（从当前时间重新计算）
        
    【场景2b: expireTime < 今天】（账号已过期）
        → 更新账号
        → startTime = 今天
        → expireTime = 原expireTime + 会员天数（累加续期）
    
    ↓
保存账号
    ↓
生成订单记录
    ↓
提交事务
```

### 3.2 流程图

```
┌─────────────────────────────────────────────────────────────┐
│                      Excel导入订单                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 解析Excel (EasyExcel)                                    │
│    - 字段: 账号、游戏类型、会员天数                            │
│    - 行级校验: 非空、游戏类型合法性、天数>0                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. 逐行处理 (独立事务)                                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 查询账号是否存在?                                         │
└─────────────────────────────────────────────────────────────┘
           │                              │
          否                             是
           │                              │
           ▼                              ▼
┌─────────────────────┐      ┌─────────────────────────────────────────┐
│ 4a. 新增账号         │      │ 4b. 更新账号                             │
│    start = 今天      │      │    判断: expireTime >= 今天?             │
│    end = 今天+天数   │      │         │              │                │
│                      │      │        是             否                │
│                      │      │         │              │                │
│                      │      │         ▼              ▼                │
│                      │      │  4b-1. 有效期内    4b-2. 已过期          │
│                      │      │  start = 不变      start = 今天          │
│                      │      │  end = 今天+天数   end = 原end+天数      │
│                      │      │  (重新计算)        (累加续期)            │
└─────────────────────┘      └─────────────────────────────────────────┘
           │                              │
           └──────────────┬───────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. 保存账号                                                 │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. 插入订单                                                 │
│    - orderNo: 自动生成                                      │
│    - userId: 取账号值                                       │
│    - gameType: Excel中的游戏类型                             │
│    - days: Excel中的会员天数                                 │
│    - status: 1 (已支付)                                     │
│    - payTime: 今天                                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 7. 提交事务                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 时间计算逻辑详解

假设今天是 **2025-02-09**

| 场景 | 原expireTime | 处理方式 | 新startTime | 新expireTime |
|------|--------------|----------|-------------|--------------|
| 账号不存在 | - | 新增 | 2025-02-09 | 2025-03-11 (今天+30天) |
| 存在且有效期内(expireTime>=今天) | 2025-03-01 | 重置 | 不变(原值) | 2025-03-11 (今天+30天) |
| 存在且已过期 | 2025-01-01 | 累加 | 2025-02-09 | 2025-03-03 (原时间+30天) |

---

## 4. API接口变更

### 4.1 接口信息

| 项目 | 内容 |
|------|------|
| 接口路径 | `POST /mall/order/importByExcel` |
| 请求方式 | POST |
| Content-Type | `multipart/form-data` |
| 权限 | `mall:order:add` |

### 4.2 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | Excel文件 |

### 4.3 响应格式

**成功响应**:
```json
{
  "code": 200,
  "msg": "导入成功，新增10条订单",
  "data": {
    "successCount": 10,
    "failCount": 0,
    "errors": []
  }
}
```

**部分失败响应**:
```json
{
  "code": 400,
  "msg": "导入完成，新增8条，失败2条",
  "data": {
    "successCount": 8,
    "failCount": 2,
    "errors": [
      {
        "rowNum": 3,
        "account": "test@example.com",
        "errorMsg": "游戏类型不合法[5]，可选值:0,1"
      },
      {
        "rowNum": 5,
        "account": "",
        "errorMsg": "账号不能为空"
      }
    ]
  }
}
```

---

## 5. 代码变更清单

### 5.1 新增DTO

**OrderImportDTO.java** - Excel导入DTO
```java
@Data
public class OrderImportDTO {
    @ExcelProperty("账号")
    private String account;
    
    @ExcelProperty("游戏类型")
    private Integer gameType;
    
    @ExcelProperty("会员天数")
    private Integer days;
}
```

**OrderImportResultDTO.java** - 导入结果DTO
```java
@Data
public class OrderImportResultDTO {
    private Integer successCount;
    private Integer failCount;
    private List<RowErrorDTO> errors;
}
```

### 5.2 修改Service

**OrderApplicationService.java** - 新增导入方法
```java
@Transactional(rollbackFor = Exception.class)
public void importOrderWithAccount(OrderImportDTO dto) {
    // 1. 查询或创建账号
    AccountEntity account = accountManager.getByUserAccount(dto.getAccount());
    
    LocalDateTime today = LocalDateTime.now();
    LocalDateTime newExpireTime;
    
    if (account == null) {
        // 新增账号
        account = new AccountEntity();
        account.setUserAccount(dto.getAccount());
        account.setType(dto.getGameType());
        account.setStartTime(today);
        newExpireTime = today.plusDays(dto.getDays());
        account.setExpireTime(newExpireTime);
        account.setEnabled(false);
        accountManager.save(account);
    } else {
        // 更新账号
        account.setType(dto.getGameType()); // 更新游戏类型
        
    if (!account.getExpireTime().isBefore(today)) {
        // expireTime >= 今天，仍在有效期内 - 只更新expireTime，startTime保持不变
        newExpireTime = today.plusDays(dto.getDays());
    } else {
        // 已过期 - 累加时间，startTime重置为今天
        account.setStartTime(today);
        newExpireTime = account.getExpireTime().plusDays(dto.getDays());
    }
        account.setExpireTime(newExpireTime);
        accountManager.updateById(account);
    }
    
    // 2. 创建订单
    OrderEntity order = new OrderEntity();
    order.setOrderNo(generateOrderNo());
    order.setUserId(dto.getAccount());
    order.setGameType(dto.getGameType());
    order.setDays(dto.getDays());
    order.setStatus(1); // 已支付
    order.setPayTime(today);
    orderManager.save(order);
}
```

### 5.3 修改Controller

**OrderController.java**
```java
@PostMapping(value = "/importByExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<OrderImportResultDTO> importByExcel(@RequestPart("file") MultipartFile file) {
    // 1. EasyExcel解析
    // 2. 逐行处理，每行独立事务
    // 3. 返回导入结果
}
```

---

## 6. 检查清单

- [ ] 创建 OrderImportDTO（Excel导入DTO）
- [ ] 创建 OrderImportResultDTO（导入结果DTO）
- [ ] 修改 OrderApplicationService（添加导入业务逻辑）
- [ ] 修改 OrderController.importByExcel（实现完整导入功能）
- [ ] 创建导入模板下载接口（可选）

---

## 7. 注意事项

1. **事务控制**: 每个Excel行独立事务，一行失败不影响其他行
2. **订单号生成**: 使用 `DateUtil.format(new Date(), "yyyyMMddHHmmss") + 随机数` 或雪花算法
3. **时间计算**: 使用 `LocalDateTime.now()` 和 `plusDays()` 方法
4. **行号**: Excel第1行是表头，数据从第2行开始，错误报告中的行号要对应

---

**变更提案版本**: v1.0  
**创建日期**: 2025-02-09  
**提案人**: AI Assistant  
**状态**: 待评审
