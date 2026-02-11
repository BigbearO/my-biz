# OpenSpec 变更提案: 代练管理系统功能扩展

## 1. 概述

### 1.1 变更目标
- 扩展代练管理系统核心功能
- 新增模拟器管理模块
- 新增执行日志模块
- 完善账号启用流程（带模拟器分配和上限校验）
- 实现订单导入自动生成账号和会员时间
- 定时任务扫描发送MQ并记录日志
- 执行统计功能（按月统计账号有效执行次数）

### 1.2 涉及模块
- `mall-api`: 新增常量、枚举、RPC接口
- `mall-biz`: 新增实体、Mapper、Manager、Service、Controller、定时任务

---

## 2. 数据库表结构设计

> **完整建表SQL脚本**: `doc/init.sql`

### 2.1 游戏账号表（account）- 新增字段

```sql
-- 现有字段保持不变，新增以下字段
ALTER TABLE account ADD COLUMN enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用';
ALTER TABLE account ADD COLUMN emulator_id BIGINT COMMENT '分配的模拟器ID';

-- 创建索引
CREATE INDEX idx_account_enabled ON account(enabled);
CREATE INDEX idx_account_emulator_id ON account(emulator_id);
```

**Entity变更** (`AccountEntity.java`):
```java
/**
 * 是否启用
 */
private Boolean enabled;

/**
 * 分配的模拟器ID
 */
private Long emulatorId;
```

### 2.2 模拟器表（emulator）- 新增表

```sql
CREATE TABLE emulator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '模拟器名称',
    ip VARCHAR(50) NOT NULL COMMENT 'IP地址',
    port INT NOT NULL COMMENT '端口',
    game_type INT NOT NULL COMMENT '游戏类型（0=原神, 1=尘白禁区）',
    max_count INT NOT NULL DEFAULT 10 COMMENT '数量上限（该模拟器最多承载账号数）',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag INT DEFAULT 0 COMMENT '删除标志（0正常 2删除）',
    INDEX idx_game_type (game_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟器表';
```

**Entity** (`EmulatorEntity.java`):
```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emulator")
public class EmulatorEntity extends BaseEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 模拟器名称 */
    private String name;
    
    /** IP地址 */
    private String ip;
    
    /** 端口 */
    private Integer port;
    
    /** 游戏类型 */
    private Integer gameType;
    
    /** 数量上限 */
    private Integer maxCount;
    
    /** 是否启用 */
    private Boolean enabled;
}
```

### 2.3 执行日志表（execution_log）- 新增表

```sql
CREATE TABLE execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_id BIGINT NOT NULL COMMENT '账号ID',
    emulator_id BIGINT NOT NULL COMMENT '模拟器ID',
    game_type INT NOT NULL COMMENT '游戏类型',
    command VARCHAR(100) COMMENT '执行命令',
    status INT DEFAULT 0 COMMENT '执行状态（0待执行 1执行中 2成功 3失败）',
    mq_message TEXT COMMENT '发送的MQ消息内容（JSON格式）',
    execute_time DATETIME COMMENT '执行时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account_id (account_id),
    INDEX idx_emulator_id (emulator_id),
    INDEX idx_game_type (game_type),
    INDEX idx_create_time (create_time),
    INDEX idx_account_date (account_id, create_time),
    INDEX idx_year_month (DATE_FORMAT(create_time, '%Y-%m'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志表';
```

**Entity** (`ExecutionLogEntity.java`):
```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("execution_log")
public class ExecutionLogEntity extends BaseEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 账号ID */
    private Long accountId;
    
    /** 模拟器ID */
    private Long emulatorId;
    
    /** 游戏类型 */
    private Integer gameType;
    
    /** 执行命令 */
    private String command;
    
    /** 执行状态 */
    private Integer status;
    
    /** MQ消息内容 */
    private String mqMessage;
    
    /** 执行时间 */
    private LocalDateTime executeTime;
}
```

---

## 3. API接口设计

### 3.1 账号启用接口

**接口**: `POST /mall/gameAccount/enable`

**Request**:
```json
{
  "accountId": 123,
  "emulatorId": 456
}
```

**Response**:
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

**业务逻辑**:
1. 校验账号是否存在
2. 校验模拟器是否存在且已启用
3. 校验模拟器当前已分配账号数 < 数量上限
4. 校验模拟器游戏类型与账号游戏类型匹配
5. 更新账号的 enabled=true, emulator_id=选择的模拟器ID

**异常场景**:
- 账号不存在 → 抛异常
- 模拟器不存在/未启用 → 抛异常
- 模拟器已达上限 → 抛异常 "该模拟器已达承载上限"
- 游戏类型不匹配 → 抛异常

### 3.2 模拟器管理CRUD接口

| 接口 | 方法 | 路径 | 说明 |
|-----|------|------|------|
| 分页查询 | GET | /mall/emulator/page | 分页查询模拟器列表 |
| 详情查询 | GET | /mall/emulator/{id} | 获取模拟器详情 |
| 新增 | POST | /mall/emulator | 新增模拟器 |
| 修改 | PUT | /mall/emulator | 修改模拟器 |
| 删除 | DELETE | /mall/emulator/{ids} | 删除模拟器 |
| 查询可用模拟器 | GET | /mall/emulator/available | 根据游戏类型查询可用模拟器 |

**可用模拟器查询** (`GET /mall/emulator/available?gameType=0`):
- 返回该游戏类型下未达上限的模拟器列表
- 用于前端选择模拟器时展示

### 3.3 执行日志查询接口

| 接口 | 方法 | 路径 | 说明 |
|-----|------|------|------|
| 分页查询 | GET | /mall/executionLog/page | 分页查询执行日志 |
| 详情查询 | GET | /mall/executionLog/{id} | 获取日志详情 |

### 3.4 统计接口（按游戏类型区分）

**接口**: `GET /mall/statistics/monthly`

**Request参数**:
- `accountId`: 账号ID（可选，不传则统计所有）
- `gameType`: 游戏类型（可选，不传则统计所有类型）
- `yearMonth`: 年月，格式 yyyy-MM（如 2025-02）

**Response**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "yearMonth": "2025-02",
    "totalCount": 150,
    "gameTypeStats": [
      {
        "gameType": 0,
        "gameName": "原神",
        "count": 100
      },
      {
        "gameType": 1,
        "gameName": "尘白禁区",
        "count": 50
      }
    ],
    "accountStats": [
      {
        "accountId": 123,
        "accountName": "user@example.com",
        "gameType": 0,
        "gameName": "原神",
        "validDays": 15
      }
    ]
  }
}
```

**统计逻辑**:
- 按账号ID + 日期分组，每个账号每天只算1次有效计数
- 支持按游戏类型筛选统计
- 返回各游戏类型的统计汇总

---

## 4. 核心业务逻辑设计

### 4.1 账号启用服务逻辑

```java
@Service
@RequiredArgsConstructor
public class AccountEnableService {
    
    private final IAccountManager accountManager;
    private final IEmulatorManager emulatorManager;
    
    @Transactional(rollbackFor = Exception.class)
    public void enableAccount(Long accountId, Long emulatorId) {
        // 1. 查询账号
        AccountEntity account = accountManager.getById(accountId);
        Assert.notNull(account, "账号不存在");
        
        // 2. 查询模拟器
        EmulatorEntity emulator = emulatorManager.getById(emulatorId);
        Assert.notNull(emulator, "模拟器不存在");
        Assert.isTrue(emulator.getEnabled(), "模拟器未启用");
        
        // 3. 校验游戏类型匹配
        Assert.isTrue(
            Objects.equals(account.getType(), emulator.getGameType()),
            "模拟器游戏类型与账号不匹配"
        );
        
        // 4. 校验模拟器是否已达上限
        long currentCount = accountManager.lambdaQuery()
            .eq(AccountEntity::getEmulatorId, emulatorId)
            .eq(AccountEntity::getEnabled, true)
            .count();
        
        Assert.isTrue(
            currentCount < emulator.getMaxCount(),
            "该模拟器已达承载上限"
        );
        
        // 5. 更新账号状态
        accountManager.lambdaUpdate()
            .set(AccountEntity::getEnabled, true)
            .set(AccountEntity::getEmulatorId, emulatorId)
            .eq(AccountEntity::getId, accountId)
            .update();
    }
}
```

### 4.2 订单导入生成账号逻辑

#### 4.2.1 导入方式支持

| 导入方式 | 接口路径 | 说明 |
|---------|---------|------|
| 单条新增 | POST /mall/account | 新增单条账号 |
| Excel导入 | POST /mall/account/importData | Excel批量导入账号 |

#### 4.2.2 Excel模板格式（简化版）

**仅3个核心字段**：

| 行号 | 账号 | 游戏类型 | 会员天数 |
|-----|------|---------|---------|
| 2 | user@example.com | 0 | 30 |
| 3 | game_account_001 | 1 | 60 |

**字段说明**:
- `账号`: 游戏账号（必填，账号唯一性校验）
- `游戏类型`: 0=原神, 1=尘白禁区
- `会员天数`: 账号有效期天数

**说明**:
- Excel每行对应生成一个账号
- `updateSupport`参数控制：如果账号已存在是否更新会员时间

#### 4.2.3 行级错误处理机制

**错误返回格式**:
```json
{
  "code": 400,
  "msg": "导入完成，存在错误",
  "data": {
    "successCount": 1,
    "failCount": 2,
    "errors": [
      {
        "rowNum": 2,
        "account": "user@example.com",
        "errorMsg": "账号已存在"
      },
      {
        "rowNum": 3,
        "account": "test_account",
        "errorMsg": "游戏类型不合法[10]，可选值:0,1; 会员天数必须大于0"
      }
    ]
  }
}
```

**校验规则**:

| 校验项 | 行级错误示例 |
|-------|-------------|
| 账号非空 | 第X行: 账号不能为空 |
| 账号唯一性 | 第X行: 账号[xxx]已存在（updateSupport=false时） |
| 游戏类型合法性 | 第X行: 游戏类型不合法[xxx]，可选值:0,1 |
| 会员天数>0 | 第X行: 会员天数必须大于0 |

#### 4.2.4 账号导入服务实现

```java
@Service
@RequiredArgsConstructor
public class AccountImportService {
    
    private final IAccountManager accountManager;
    
    /**
     * Excel导入账号
     * @param excelList Excel解析后的数据列表
     * @param updateSupport 是否支持更新（账号已存在时是否更新会员时间）
     * @return 导入结果，包含行级错误信息
     */
    public AccountImportResult importByExcel(List<AccountImportDTO> excelList, boolean updateSupport) {
        List<RowErrorDTO> errors = new ArrayList<>();
        int successCount = 0;
        int updateCount = 0;
        
        for (int i = 0; i < excelList.size(); i++) {
            AccountImportDTO dto = excelList.get(i);
            int rowNum = i + 2; // Excel从第2行开始（第1行是表头）
            
            // 行级校验
            List<String> rowErrors = validateAccountRow(dto, rowNum, updateSupport);
            if (!rowErrors.isEmpty()) {
                errors.add(new RowErrorDTO(rowNum, dto.getUserAccount(), 
                    String.join("; ", rowErrors)));
                continue;
            }
            
            try {
                // 导入单个账号
                boolean isUpdate = importSingleAccount(dto, updateSupport);
                if (isUpdate) {
                    updateCount++;
                } else {
                    successCount++;
                }
            } catch (Exception e) {
                errors.add(new RowErrorDTO(rowNum, dto.getUserAccount(), 
                    "系统错误: " + e.getMessage()));
            }
        }
        
        return new AccountImportResult(successCount, updateCount, errors.size(), errors);
    }
    
    /**
     * 行级校验
     */
    private List<String> validateAccountRow(AccountImportDTO dto, int rowNum, boolean updateSupport) {
        List<String> errors = new ArrayList<>();
        
        // 1. 账号非空
        if (StrUtil.isBlank(dto.getUserAccount())) {
            errors.add("账号不能为空");
            return errors;
        }
        
        // 2. 账号唯一性校验（如果不支持更新）
        if (!updateSupport) {
            AccountEntity existAccount = accountManager.getByUserAccount(dto.getUserAccount());
            if (existAccount != null) {
                errors.add("账号[" + dto.getUserAccount() + "]已存在");
            }
        }
        
        // 3. 游戏类型合法性
        if (dto.getGameType() == null || !GamesEnum.isValid(dto.getGameType())) {
            errors.add("游戏类型不合法[" + dto.getGameType() + "]，可选值:0,1");
        }
        
        // 4. 会员天数
        if (dto.getDays() == null || dto.getDays() <= 0) {
            errors.add("会员天数必须大于0");
        }
        
        return errors;
    }
    
    /**
     * 导入单个账号
     * @return true-更新, false-新增
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean importSingleAccount(AccountImportDTO dto, boolean updateSupport) {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime expireTime = startTime.plusDays(dto.getDays());
        
        // 查询账号是否已存在
        AccountEntity existAccount = accountManager.getByUserAccount(dto.getUserAccount());
        
        if (existAccount != null && updateSupport) {
            // 更新现有账号的会员时间
            accountManager.lambdaUpdate()
                .set(AccountEntity::getStartTime, startTime)
                .set(AccountEntity::getExpireTime, expireTime)
                .set(AccountEntity::getType, dto.getGameType())
                .eq(AccountEntity::getId, existAccount.getId())
                .update();
            return true;
        } else {
            // 新增账号
            AccountEntity account = new AccountEntity();
            account.setUserAccount(dto.getUserAccount().trim());
            account.setType(dto.getGameType());
            account.setStartTime(startTime);
            account.setExpireTime(expireTime);
            account.setEnabled(false); // 初始未启用
            account.setEmulatorId(null);
            
            accountManager.save(account);
            return false;
        }
    }
}
```

#### 4.2.5 DTO定义

```java
/**
 * Excel导入账号DTO
 */
@Data
public class AccountImportDTO {
    
    @ExcelProperty("账号")
    private String userAccount;
    
    @ExcelProperty("游戏类型")
    private Integer gameType;
    
    @ExcelProperty("会员天数")
    private Integer days;
}

/**
 * 行级错误DTO
 */
@Data
@AllArgsConstructor
public class RowErrorDTO {
    /** Excel行号 */
    private Integer rowNum;
    /** 账号 */
    private String account;
    /** 错误信息 */
    private String errorMsg;
}

/**
 * 导入结果
 */
@Data
@AllArgsConstructor
public class AccountImportResult {
    /** 新增成功数 */
    private Integer successCount;
    /** 更新成功数 */
    private Integer updateCount;
    /** 失败数 */
    private Integer failCount;
    /** 错误列表 */
    private List<RowErrorDTO> errors;
}
```

#### 4.2.6 Controller接口（使用EasyExcel）

```java
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mall/account")
public class AccountController extends BaseController {
    
    private final AccountImportService accountImportService;
    
    /**
     * 单条新增账号
     */
    @PostMapping
    public R<Long> add(@RequestBody @Validated AccountCreateDTO dto) {
        Long accountId = accountImportService.createSingleAccount(dto);
        return R.ok(accountId);
    }
    
    /**
     * Excel导入账号（使用EasyExcel）
     * @param file Excel文件
     * @param updateSupport 是否支持更新（账号已存在时更新会员时间）
     */
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AccountImportResult> importData(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean updateSupport) throws Exception {
        
        // 使用EasyExcel解析
        ExcelResult<AccountImportDTO> excelResult = ExcelUtil.importExcel(
            file.getInputStream(), 
            AccountImportDTO.class, 
            new AccountImportListener(updateSupport)
        );
        
        // 获取解析后的数据
        List<AccountImportDTO> list = excelResult.getList();
        
        // 执行业务导入
        AccountImportResult result = accountImportService.importByExcel(list, updateSupport);
        
        // 合并Excel解析错误和业务错误
        List<RowErrorDTO> allErrors = new ArrayList<>();
        if (excelResult.getErrorList() != null) {
            allErrors.addAll(excelResult.getErrorList().stream()
                .map(e -> new RowErrorDTO(e.getRowNum(), null, e.getMessage()))
                .toList());
        }
        allErrors.addAll(result.getErrors());
        
        result.setErrors(allErrors);
        result.setFailCount(allErrors.size());
        
        // 返回导入结果
        if (result.getFailCount() > 0) {
            return R.fail("导入完成，新增" + result.getSuccessCount() 
                + "条，更新" + result.getUpdateCount() 
                + "条，失败" + result.getFailCount() + "条", result);
        }
        
        return R.ok(result);
    }
    
    /**
     * 下载导入模板
     */
    @GetMapping("/importTemplate")
    public void downloadTemplate(HttpServletResponse response) {
        // 创建模板数据
        List<AccountImportDTO> templateList = List.of(
            createTemplateRow("user@example.com", 0, 30),
            createTemplateRow("game_account_001", 1, 60)
        );
        ExcelUtil.exportExcel(response, templateList, "账号导入模板", AccountImportDTO.class);
    }
    
    private AccountImportDTO createTemplateRow(String account, Integer gameType, Integer days) {
        AccountImportDTO dto = new AccountImportDTO();
        dto.setUserAccount(account);
        dto.setGameType(gameType);
        dto.setDays(days);
        return dto;
    }
}

/**
 * EasyExcel导入监听器
 */
@Slf4j
public class AccountImportListener extends AnalysisEventListener<AccountImportDTO> {
    
    private final boolean updateSupport;
    private final List<AccountImportDTO> list = new ArrayList<>();
    private final List<ExcelErrorDTO> errorList = new ArrayList<>();
    
    public AccountImportListener(boolean updateSupport) {
        this.updateSupport = updateSupport;
    }
    
    @Override
    public void invoke(AccountImportDTO dto, AnalysisContext context) {
        // 基础格式校验
        Integer rowNum = context.readRowHolder().getRowIndex() + 1;
        
        if (StrUtil.isBlank(dto.getUserAccount())) {
            errorList.add(new ExcelErrorDTO(rowNum, null, "账号不能为空"));
            return;
        }
        
        list.add(dto);
    }
    
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Excel解析完成，共{}条数据", list.size());
    }
    
    public List<AccountImportDTO> getList() {
        return list;
    }
    
    public List<ExcelErrorDTO> getErrorList() {
        return errorList;
    }
}
```

#### 4.2.7 导出错误报告功能

**需求**: 导入失败后，可导出包含错误信息的Excel，方便用户修正后重新导入

**错误报告Excel格式**:

| 原始行号 | 账号 | 游戏类型 | 会员天数 | 错误信息 |
|---------|------|---------|---------|---------|
| 2 | user@example.com | 0 | 30 | 账号已存在 |
| 5 | test_account | 10 | -5 | 游戏类型不合法[10]; 会员天数必须大于0 |

**实现代码**:

```java
/**
 * 导出导入错误报告
 */
@PostMapping("/exportErrorReport")
public void exportErrorReport(@RequestBody AccountImportResult result, HttpServletResponse response) {
    List<AccountErrorExportDTO> errorList = result.getErrors().stream()
        .map(error -> {
            AccountErrorExportDTO dto = new AccountErrorExportDTO();
            dto.setRowNum(error.getRowNum());
            dto.setUserAccount(error.getAccount());
            dto.setErrorMsg(error.getErrorMsg());
            // 回填原始数据...
            return dto;
        }).toList();
    
    ExcelUtil.exportExcel(response, errorList, "导入错误报告", AccountErrorExportDTO.class);
}

/**
 * 错误报告导出DTO
 */
@Data
public class AccountErrorExportDTO {
    @ExcelProperty("原始行号")
    private Integer rowNum;
    
    @ExcelProperty("账号")
    private String userAccount;
    
    @ExcelProperty("游戏类型")
    private Integer gameType;
    
    @ExcelProperty("会员天数")
    private Integer days;
    
    @ExcelProperty("错误信息")
    private String errorMsg;
}
```

#### 4.2.8 导入流程图

```
用户上传Excel
    ↓
解析Excel为List<OrderExcelDTO>
    ↓
逐行遍历处理
    ↓
行级校验（非空、格式、唯一性等）
    ↓
校验通过? 
    ↓ 否 → 收集错误信息(行号+错误描述) → 继续下一行
    ↓ 是
开启事务
    ↓
保存订单主表
    ↓
解析账号信息(按|分割)
    ↓
循环生成账号 → 计算会员时间 → 保存账号
    ↓
提交事务
    ↓
返回导入结果
    ↓
存在错误? 
    ↓ 是 → 提示"导入完成，存在X条错误" → 提供下载错误报告
    ↓ 否 → 提示"导入成功"
```

#### 4.2.9 导入检查清单

| 检查项 | 说明 |
|-------|------|
| 行号准确性 | Excel第1行是表头，数据从第2行开始，错误报告行号要与原始Excel一致 |
| 批量事务 | 每行数据独立事务，一行失败不影响其他行导入 |
| 错误收集 | 单行可能有多个错误，用分号分隔 |
| updateSupport | true时账号存在则更新会员时间，false时报错 |
| 性能 | 大批量导入时（>1000行），建议分批处理 |
| 模板下载 | 提供标准模板下载，包含示例数据 |
| EasyExcel | 使用EasyExcel进行Excel解析，支持大文件 |

### 4.3 定时任务发送MQ逻辑

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberTask {
    
    private final IAccountManager accountManager;
    private final IEmulatorManager emulatorManager;
    private final IExecutionLogManager executionLogManager;
    private final GameTaskSender gameTaskSender;
    
    @Scheduled(cron = "0 */10 * * * *")
    public void sendAutoTask() {
        log.info("开始执行会员定时任务");
        
        LocalDateTime now = LocalDateTime.now();
        
        // 查询在会员期内且已启用的账号
        List<AccountEntity> accounts = accountManager.lambdaQuery()
            .le(AccountEntity::getStartTime, now)
            .ge(AccountEntity::getExpireTime, now)
            .eq(AccountEntity::getEnabled, true)
            .isNotNull(AccountEntity::getEmulatorId)
            .list();
        
        for (AccountEntity account : accounts) {
            try {
                // 查询模拟器信息
                EmulatorEntity emulator = emulatorManager.getById(account.getEmulatorId());
                if (emulator == null || !emulator.getEnabled()) {
                    log.warn("账号{}的模拟器不可用，跳过", account.getId());
                    continue;
                }
                
                // 构建MQ消息
                MQMessageDTO message = MQMessageDTO.builder()
                    .configName("Alas")
                    .command("daily_quest")
                    .emulatorIp(emulator.getIp())
                    .emulatorPort(String.valueOf(emulator.getPort()))
                    .priority(1)
                    .accountId(account.getId())
                    .build();
                
                // 发送MQ
                gameTaskSender.send2Auto(message, account.getType());
                
                // 记录执行日志
                ExecutionLogEntity logEntity = new ExecutionLogEntity();
                logEntity.setAccountId(account.getId());
                logEntity.setEmulatorId(emulator.getId());
                logEntity.setGameType(account.getType());
                logEntity.setCommand("daily_quest");
                logEntity.setStatus(0); // 待执行
                logEntity.setMqMessage(JSON.toJSONString(message));
                logEntity.setExecuteTime(now);
                executionLogManager.save(logEntity);
                
            } catch (Exception e) {
                log.error("发送任务失败, 账号: {}", account.getId(), e);
            }
        }
        
        log.info("会员定时任务执行完成，处理账号数: {}", accounts.size());
    }
}
```

---

## 5. Mapper XML手写SQL设计

### 5.1 统计每月有效执行次数（按游戏类型区分）

**Mapper接口** (`ExecutionLogMapper.java`):
```java
public interface ExecutionLogMapper extends BaseMapper<ExecutionLogEntity> {
    
    /**
     * 按月统计账号有效执行次数
     * 每个账号每天只算1次有效计数
     */
    List<MonthlyStatisticsVO> selectMonthlyStatistics(
        @Param("yearMonth") String yearMonth,
        @Param("accountId") Long accountId,
        @Param("gameType") Integer gameType
    );
    
    /**
     * 统计总有效次数
     */
    Long selectTotalValidCount(
        @Param("yearMonth") String yearMonth,
        @Param("gameType") Integer gameType
    );
    
    /**
     * 按游戏类型统计有效次数
     */
    List<GameTypeStatisticsVO> selectStatisticsByGameType(
        @Param("yearMonth") String yearMonth
    );
}
```

**Mapper XML** (`ExecutionLogMapper.xml`):
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bigbear.mall.biz.mapper.ExecutionLogMapper">

    <!-- 按月统计账号有效执行次数（按游戏类型区分） -->
    <select id="selectMonthlyStatistics" resultType="com.bigbear.mall.biz.domain.vo.MonthlyStatisticsVO">
        SELECT 
            el.account_id AS accountId,
            a.user_account AS accountName,
            el.game_type AS gameType,
            COUNT(DISTINCT DATE(el.create_time)) AS validDays
        FROM execution_log el
        LEFT JOIN account a ON el.account_id = a.id
        WHERE DATE_FORMAT(el.create_time, '%Y-%m') = #{yearMonth}
        <if test="accountId != null">
            AND el.account_id = #{accountId}
        </if>
        <if test="gameType != null">
            AND el.game_type = #{gameType}
        </if>
        GROUP BY el.account_id, el.game_type
    </select>

    <!-- 统计总有效次数（支持按游戏类型筛选） -->
    <select id="selectTotalValidCount" resultType="java.lang.Long">
        SELECT 
            COUNT(*)
        FROM (
            SELECT DISTINCT 
                account_id,
                DATE(create_time)
            FROM execution_log
            WHERE DATE_FORMAT(create_time, '%Y-%m') = #{yearMonth}
            <if test="gameType != null">
                AND game_type = #{gameType}
            </if>
        ) t
    </select>
    
    <!-- 按游戏类型分组统计 -->
    <select id="selectStatisticsByGameType" resultType="com.bigbear.mall.biz.domain.vo.GameTypeStatisticsVO">
        SELECT 
            game_type AS gameType,
            COUNT(*) AS count
        FROM (
            SELECT DISTINCT 
                game_type,
                account_id,
                DATE(create_time)
            FROM execution_log
            WHERE DATE_FORMAT(create_time, '%Y-%m') = #{yearMonth}
        ) t
        GROUP BY game_type
    </select>

</mapper>
```

**VO对象**:
```java
@Data
public class MonthlyStatisticsVO {
    private Long accountId;
    private String accountName;
    private Integer gameType;
    private Long validDays;
}

@Data
public class GameTypeStatisticsVO {
    private Integer gameType;
    private Long count;
}
```

### 5.2 查询模拟器当前承载数量

```xml
<select id="selectEmulatorAccountCount" resultType="java.lang.Long">
    SELECT COUNT(*)
    FROM account
    WHERE emulator_id = #{emulatorId}
    AND enabled = 1
    AND del_flag = 0
</select>
```

---

## 6. MQ消息格式

### 6.1 发送消息格式

```java
@Data
@Builder
public class MQMessageDTO {
    private String configName;      // "Alas"
    private String command;         // "daily_quest"
    private String emulatorIp;      // 模拟器IP
    private String emulatorPort;    // 模拟器端口
    private Integer priority;       // 优先级
    private Long accountId;         // 账号ID（扩展字段）
}
```

**JSON示例**:
```json
{
  "config_name": "Alas",
  "command": "daily_quest",
  "emulator_ip": "127.0.0.1",
  "emulator_port": "5555",
  "priority": 1,
  "account_id": 123
}
```

---

## 7. 目录结构变更

### 7.1 新增文件清单

**API模块** (`mall-api`):
```
com/bigbear/mall/api/
└── common/
    └── enums/
        └── EmulatorStatusEnum.java    # 模拟器状态枚举
```

**Biz模块** (`mall-biz`):
```
com/bigbear/mall/biz/
├── domain/
│   ├── entity/
│   │   ├── EmulatorEntity.java          # 模拟器实体
│   │   └── ExecutionLogEntity.java      # 执行日志实体
│   ├── bo/
│   │   ├── AccountEnableBO.java         # 账号启用BO
│   │   └── OrderCreateBO.java           # 订单创建BO
│   ├── dto/
│   │   ├── MQMessageDTO.java            # MQ消息DTO
│   │   ├── AccountImportDTO.java        # 账号导入DTO
│   │   ├── RowErrorDTO.java             # 行级错误DTO
│   │   ├── AccountImportResult.java     # 导入结果DTO
│   │   ├── AccountErrorExportDTO.java   # 错误报告导出DTO
│   │   └── MonthlyStatisticsDTO.java    # 月度统计DTO
│   └── vo/
│       ├── EmulatorVO.java              # 模拟器VO
│       ├── ExecutionLogVO.java          # 执行日志VO
│       ├── MonthlyStatisticsVO.java     # 月度统计VO
│       ├── GameTypeStatisticsVO.java    # 游戏类型统计VO
│       └── AvailableEmulatorVO.java     # 可用模拟器VO
├── mapper/
│   ├── EmulatorMapper.java
│   ├── ExecutionLogMapper.java
│   └── xml/
│       ├── EmulatorMapper.xml
│       └── ExecutionLogMapper.xml
├── manager/
│   ├── IEmulatorManager.java
│   ├── IExecutionLogManager.java
│   └── impl/
│       ├── EmulatorManagerImpl.java
│       └── ExecutionLogManagerImpl.java
├── application/
│   └── service/
│       ├── AccountEnableService.java    # 账号启用服务
│       ├── AccountImportService.java    # 账号导入服务（含Excel）
│       └── StatisticsService.java       # 统计服务
└── trigger/
    ├── api/
    │   ├── EmulatorController.java      # 模拟器控制器
    │   ├── ExecutionLogController.java  # 执行日志控制器
    │   └── StatisticsController.java    # 统计控制器
    └── task/
        └── MemberTask.java              # 定时任务（修改）
```

---

## 8. 关键业务流程

### 8.1 账号启用流程

```
用户请求启用账号
    ↓
校验账号存在
    ↓
校验模拟器存在且启用
    ↓
校验游戏类型匹配
    ↓
查询模拟器当前承载数
    ↓
判断: 当前数 < 上限?
    ↓ 否 → 报错"模拟器已达上限"
    ↓ 是
更新账号(enabled=true, emulator_id=xxx)
    ↓
返回成功
```

### 8.2 账号导入流程

```
上传Excel/调用接口
    ↓
EasyExcel解析数据
    ↓
行级校验（账号非空、唯一性、游戏类型、会员天数）
    ↓
校验通过?
    ↓ 否 → 收集错误(行号+错误描述) → 继续下一行
    ↓ 是
判断: updateSupport=true 且 账号已存在?
    ↓ 是 → 更新会员时间
    ↓ 否 → 新增账号(初始enabled=false)
    ↓
返回导入结果(新增数/更新数/失败数)
```

### 8.3 定时任务执行流程

```
定时触发(每10分钟)
    ↓
查询在会员期内且已启用的账号
    ↓
循环处理每个账号
    ↓
查询账号分配的模拟器
    ↓
构建MQ消息(包含模拟器IP/端口)
    ↓
发送MQ消息
    ↓
插入执行日志
    ↓
记录执行结果
```

---

## 9. 检查清单

### 9.1 数据库变更
- [ ] 账号表新增 enabled 字段
- [ ] 账号表新增 emulator_id 字段
- [ ] 创建 emulator 表
- [ ] 创建 execution_log 表

### 9.2 实体类
- [ ] AccountEntity 新增字段
- [ ] EmulatorEntity 新建
- [ ] ExecutionLogEntity 新建

### 9.3 Mapper层
- [ ] EmulatorMapper.java + XML
- [ ] ExecutionLogMapper.java + XML (含手写统计SQL)

### 9.4 Manager层
- [ ] IEmulatorManager / EmulatorManagerImpl
- [ ] IExecutionLogManager / ExecutionLogManagerImpl

### 9.5 业务逻辑
- [ ] 账号启用服务 (含上限校验)
- [ ] 账号导入服务 (生成账号+会员时间)
- [ ] Excel导入服务 (EasyExcel+行级错误处理)
- [ ] 定时任务 (发送MQ+记录日志)
- [ ] 统计服务 (手写SQL查询，按游戏类型区分)

### 9.6 API接口
- [ ] EmulatorController (CRUD + 查询可用)
- [ ] AccountController 新增启用接口+Excel导入+模板下载
- [ ] ExecutionLogController (查询)
- [ ] StatisticsController (月度统计，按游戏类型区分)

---

## 10. 附录

### 10.1 状态码定义

| 状态码 | 说明 |
|-------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 404 | 资源不存在 |
| 409 | 模拟器已达上限 |
| 500 | 系统错误 |

### 10.2 游戏类型枚举

沿用现有 `GamesEnum`:
- 0: 原神
- 1: 尘白禁区

---

**变更提案版本**: v1.0  
**创建日期**: 2025-02-09  
**提案人**: AI Assistant  
**状态**: 待评审
