# OrderImportListener 和 OrderApplicationService 重构提案

## 变更概述

重构订单Excel导入逻辑，将批量处理方式改为逐行实时处理，并在监听器中实时收集成功/失败消息。

---

## 当前问题

1. `OrderImportListener.invoke` 方法为空，未实现业务逻辑
2. `importOrdersWithAccount` 是批量处理方法，与EasyExcel逐行监听模式不匹配
3. 缺少实时成功/失败消息收集机制
4. `importSingleOrderWithAccount` 缺少订单存在性校验

---

## 变更方案

### 一、OrderApplicationService 改造

#### 1.1 修改 importSingleOrderWithAccount 方法

**变更内容：** 在方法开头增加订单号校验（非空判断 + 存在性校验）

```java
/**
 * 导入单条订单并处理账号（事务方法）
 * 【改造点】增加订单号校验：
 * 1. 如果orderNo为空，提示错误
 * 2. 如果orderNo已存在，提示订单已处理
 */
@Transactional(rollbackFor = Exception.class)
public void importSingleOrderWithAccount(OrderImportDTO dto) {
    LocalDateTime today = LocalDateTime.now();
    LocalDateTime newExpireTime;

    // 【改造点】0. 校验订单号
    // 0.1 判断orderNo是否为空
    if (StrUtil.isBlank(dto.getOrderNo())) {
        throw new ServiceException("订单编号不能为空");
    }
    
    // 0.2 判断订单是否已存在
    OrderEntity existOrder = orderManager.getByOrderNo(dto.getOrderNo());
    if (existOrder != null) {
        throw new ServiceException("订单已经处理: " + dto.getOrderNo());
    }

    // 1. 查询或创建账号...
    // ... 原有逻辑保持不变 ...

    // 2. 创建订单（使用Excel中的orderNo）
    OrderEntity order = new OrderEntity();
    order.setOrderNo(dto.getOrderNo());  // 使用Excel中的orderNo，不再预生成
    // ... 其他字段设置 ...
    orderManager.save(order);
}
```

**关键变更点：**
- 从Excel数据中获取 `orderNo`（`dto.getOrderNo()`），不再预生成
- 如果 `orderNo` 为空，抛异常：`订单编号不能为空`
- 如果 `orderNo` 已存在，抛异常：`订单已经处理: {orderNo}`
- 使用Excel中的 `orderNo` 创建订单

---

#### 1.2 修改 importOrdersWithAccount 方法（单条处理版本）

**变更前：**
```java
public OrderImportResultDTO importOrdersWithAccount(List<OrderImportDTO> dtoList) {
    // 批量处理逻辑...
}
```

**变更后：**
```java
/**
 * 导入单条订单并处理账号逻辑（事务方法）
 * 
 * @param dto 单条订单导入数据
 * @throws ServiceException 业务异常（如订单已存在、校验失败等）
 */
@Transactional(rollbackFor = Exception.class)
public void importOrdersWithAccount(OrderImportDTO dto) {
    // 1. 行级校验
    List<String> errors = validateImportRow(dto);
    if (!errors.isEmpty()) {
        throw new ServiceException(String.join("; ", errors));
    }
    
    // 2. 执行导入（复用改造后的 importSingleOrderWithAccount 逻辑）
    importSingleOrderWithAccount(dto);
}
```

**关键变更点：**
- 入参从 `List<OrderImportDTO>` 改为 `OrderImportDTO`
- 无返回值，成功则静默完成，失败抛异常
- 异常使用 `ServiceException` 抛出具体错误信息
- 保留事务注解，确保单条数据处理的原子性

---

### 二、OrderImportListener 改造

#### 2.1 修改 invoke 方法

**用户确认：失败消息才显示行号**

**变更前：**
```java
@Override
public void invoke(OrderImportDTO dto, AnalysisContext context) {
    int rowNum = context.readRowHolder().getRowIndex() + 1;
    // 空实现...
}
```

**变更后：**
```java
@Override
public void invoke(OrderImportDTO dto, AnalysisContext context) {
    int rowNum = context.readRowHolder().getRowIndex() + 1;
    
    try {
        // 调用服务层处理单条订单（带事务）
        orderApplicationService.importOrdersWithAccount(dto);
        
        // 【成功消息】不显示行号，格式：序号、订单账号 {account} 导入成功
        successNum++;
        successMsg.append("<br/>").append(successNum).append("、订单账号 ")
                .append(dto.getAccount()).append(" 导入成功");
        
    } catch (ServiceException e) {
        // 【失败消息】显示行号，格式：序号、第{rowNum}行 订单账号 {account} {errorMsg}
        failureNum++;
        failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                .append("行 订单账号 ").append(dto.getAccount())
                .append(" ").append(e.getMessage());
                
    } catch (Exception e) {
        // 【系统异常】同样显示行号
        failureNum++;
        failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                .append("行 订单账号 ").append(dto.getAccount())
                .append(" 导入失败：").append(e.getMessage());
        log.error("订单导入失败，rowNum={}, account={}", rowNum, dto.getAccount(), e);
    }
}
```

**关键变更点：**
- **成功消息**：`序号、订单账号 {account} 导入成功`（无行号）
- **失败消息**：`序号、第{rowNum}行 订单账号 {account} {errorMsg}`（有行号）
- 调用改造后的 `importOrdersWithAccount` 单条处理方法
- 无返回值，通过异常判断成功/失败

---

### 三、Controller 层调整

#### 3.1 修改 importByExcel 方法

**用户确认：OrderImportResultDTO 保留，但不返回统计信息**

**变更前：**
```java
@PostMapping(value = "/importByExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<OrderImportResultDTO> importByExcel(@RequestPart("file") MultipartFile file) throws IOException {
    // 1. 使用EasyExcel解析
    OrderImportListener listener = new OrderImportListener();
    val orderImportDTOExcelResult = ExcelUtil.importExcel(file.getInputStream(), OrderImportDTO.class, listener);

    // 2. 获取解析后的数据
    List<OrderImportDTO> list = listener.getList();

    // 3. 执行业务导入
    OrderImportResultDTO result = orderApplicationService.importOrdersWithAccount(list);

    // 4. 合并Excel解析错误
    // ...
}
```

**变更后：**
```java
@Operation(summary = "Excel导入订单", description = "Excel导入订单，自动处理账号会员时间")
@SaCheckPermission("mall:order:add")
@Log(title = "订单管理", businessType = BusinessType.IMPORT)
@PostMapping(value = "/importByExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<String> importByExcel(@RequestPart("file") MultipartFile file) throws IOException {
    // 使用EasyExcel解析（在解析过程中逐行处理业务逻辑）
    OrderImportListener listener = new OrderImportListener();
    ExcelUtil.importExcel(file.getInputStream(), OrderImportDTO.class, listener);
    
    // 获取处理结果消息（由listener.getExcelResult()返回）
    return R.ok(listener.getExcelResult().getAnalysis());
}
```

**关键变更点：**
- 返回值从 `R<OrderImportResultDTO>` 改为 `R<String>`（只返回消息字符串）
- 移除手动调用批量处理方法
- 业务逻辑在EasyExcel解析时逐行触发
- `OrderImportResultDTO` 类保留但不在本接口使用（供其他地方使用）

---

## 完整代码实现

### OrderApplicationService 变更代码

```java
/**
 * 导入单条订单并处理账号逻辑（事务方法）
 * 
 * @param dto 单条订单导入数据
 * @throws ServiceException 业务异常
 */
@Transactional(rollbackFor = Exception.class)
public void importOrdersWithAccount(OrderImportDTO dto) {
    // 1. 行级校验（包含orderNo非空、游戏类型、会员天数等）
    List<String> errors = validateImportRow(dto);
    if (!errors.isEmpty()) {
        throw new ServiceException(String.join("; ", errors));
    }
    
    // 2. 执行导入（复用改造后的单条处理方法）
    importSingleOrderWithAccount(dto);
}

/**
 * 导入单条订单并处理账号（事务方法）
 * 【改造点】增加订单号校验：非空判断 + 存在性校验
 */
@Transactional(rollbackFor = Exception.class)
public void importSingleOrderWithAccount(OrderImportDTO dto) {
    LocalDateTime today = LocalDateTime.now();
    LocalDateTime newExpireTime;

    // 【改造点】0. 校验订单号
    // 0.1 判断orderNo是否为空
    if (StrUtil.isBlank(dto.getOrderNo())) {
        throw new ServiceException("订单编号不能为空");
    }
    
    // 0.2 判断订单是否已存在
    OrderEntity existOrder = orderManager.getByOrderNo(dto.getOrderNo());
    if (existOrder != null) {
        throw new ServiceException("订单已经处理: " + dto.getOrderNo());
    }

    // 1. 查询或创建账号
    AccountEntity account = accountManager.lambdaQuery()
            .eq(AccountEntity::getUserAccount, dto.getAccount())
            .one();

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
        log.debug("新增账号，account={}", dto.getAccount());
    } else {
        // 更新账号
        account.setType(dto.getGameType());

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
        log.debug("更新账号，account={}, expireTime={}", dto.getAccount(), newExpireTime);
    }

    // 2. 创建订单（使用Excel中的orderNo，不再预生成）
    OrderEntity order = new OrderEntity();
    order.setOrderNo(dto.getOrderNo());  // 【变更】使用Excel中的orderNo
    order.setUserId(dto.getAccount());
    order.setGameType(dto.getGameType());
    order.setDays(dto.getDays());
    order.setStatus(1); // 已支付
    order.setPayTime(today);
    orderManager.save(order);

    log.debug("创建订单成功，orderNo={}", dto.getOrderNo());
}
```

### OrderImportListener 变更代码

```java
@Override
public void invoke(OrderImportDTO dto, AnalysisContext context) {
    int rowNum = context.readRowHolder().getRowIndex() + 1;
    
    try {
        // 调用服务层处理单条订单（带事务）
        orderApplicationService.importOrdersWithAccount(dto);
        
        // 成功：不显示行号
        successNum++;
        successMsg.append("<br/>").append(successNum).append("、订单账号 ")
                .append(dto.getAccount()).append(" 导入成功");
        
    } catch (ServiceException e) {
        // 失败：显示行号
        failureNum++;
        failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                .append("行 订单账号 ").append(dto.getAccount())
                .append(" ").append(e.getMessage());
                
    } catch (Exception e) {
        // 系统异常：显示行号
        failureNum++;
        failureMsg.append("<br/>").append(failureNum).append("、第").append(rowNum)
                .append("行 订单账号 ").append(dto.getAccount())
                .append(" 导入失败：").append(e.getMessage());
        log.error("订单导入失败，rowNum={}, account={}", rowNum, dto.getAccount(), e);
    }
}
```

### OrderController 变更代码

```java
@Operation(summary = "Excel导入订单", description = "Excel导入订单，自动处理账号会员时间")
@SaCheckPermission("mall:order:add")
@Log(title = "订单管理", businessType = BusinessType.IMPORT)
@PostMapping(value = "/importByExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public R<String> importByExcel(@RequestPart("file") MultipartFile file) throws IOException {
    OrderImportListener listener = new OrderImportListener();
    ExcelUtil.importExcel(file.getInputStream(), OrderImportDTO.class, listener);
    return R.ok(listener.getExcelResult().getAnalysis());
}
```

---

## 消息格式示例

### 全部成功（无行号）
```
恭喜您，数据已全部导入成功！共 3 条，数据如下：
1、订单账号 user001 导入成功
2、订单账号 user002 导入成功
3、订单账号 user003 导入成功
```

### 部分失败（有行号）
```
很抱歉，导入失败！共 2 条数据格式不正确，错误如下：
1、第2行 订单账号 user001 账号不能为空
2、第5行 订单账号 user002 订单已经处理: ORDER123456789
```

---

## 已确认事项

| 序号 | 问题 | 确认结果 |
|------|------|---------|
| 1 | 消息显示行号 | **失败消息才显示行号**，成功消息不显示 |
| 2 | 事务粒度 | **每行独立事务**符合预期，但`importSingleOrderWithAccount`需增加订单存在性校验 |
| 3 | OrderImportResultDTO | **保留**（类保留但不在本接口返回） |
| 4 | 成功/失败统计 | **不返回**统计信息，只返回消息字符串 |

---

## 影响范围

| 文件 | 变更类型 | 影响说明 |
|------|---------|---------|
| `OrderApplicationService.java` | 修改 | `importOrdersWithAccount` 改为单条处理，`importSingleOrderWithAccount` 增加订单存在性校验 |
| `OrderImportListener.java` | 修改 | `invoke` 方法实现业务逻辑，区分成功/失败消息格式 |
| `OrderController.java` | 修改 | `importByExcel` 方法简化，返回类型改为 `R<String>` |
| `OrderImportResultDTO.java` | 保留 | 类保留，供其他地方使用 |

---

## 实施计划

1. **Step 1**: 修改 `OrderApplicationService.importSingleOrderWithAccount`，增加订单存在性校验
2. **Step 2**: 修改 `OrderApplicationService.importOrdersWithAccount`，改为单条处理
3. **Step 3**: 修改 `OrderImportListener.invoke`，实现逐行处理逻辑
4. **Step 4**: 修改 `OrderController.importByExcel`，简化接口返回

---

请确认以上方案后，我将开始实施具体的代码修改。
