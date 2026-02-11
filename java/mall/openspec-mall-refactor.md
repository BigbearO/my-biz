# OpenSpec 变更提案: Mall 模块重构与 Order 模块迁移

## 1. 概述

### 1.1 变更目标
- 参考 `ai` 模块结构，在 `mall` 目录下创建 `mall-api` 和 `mall-biz` 模块
- 将 `order` 模块的 `order-api` 和 `order-biz` 迁移到 `mall` 模块
- 将依赖从 Pig 框架迁移到 RuoYi-Cloud-Plus 框架
- 统一使用 ruoyi-plus 依赖，去除 pig 依赖

### 1.2 参考标准
- **AI 模块**: `com.bigbear:ai:1.0.0` (基于 RuoYi-Cloud-Plus 2.5.2)
- **目标框架**: RuoYi-Cloud-Plus 2.5.2
- **Java 版本**: 21

---

## 2. 目录结构规范

### 2.1 mall-api 模块结构
```
mall-api/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/bigbear/mall/api/
                ├── common/
                │   ├── constants/      # 常量定义
                │   └── enums/          # 枚举类
                └── rpc/                # RPC/Feign 接口定义
```

### 2.2 mall-biz 模块结构
```
mall-biz/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/bigbear/mall/biz/
        │       ├── MallApplication.java
        │       ├── common/             # 公共部分
        │       │   ├── config/       # 配置类
        │       │   ├── constants/    # 常量
        │       │   └── enums/        # 枚举
        │       ├── domain/           # 领域层/实体层
        │       │   ├── entity/       # 数据库实体类
        │       │   ├── dto/          # 数据传输对象
        │       │   ├── bo/           # 业务对象
        │       │   └── vo/           # 视图对象
        │       ├── mapper/           # mybatis mapper 层
        │       ├── manager/          # 基础 mybatis-plus IService/ServiceImpl
        │       │   ├── IOrderManager.java      # 对应原 Repository 接口
        │       │   └── impl/
        │       │       └── OrderManagerImpl.java
        │       ├── application/      # 应用服务层（复杂业务编排）
        │       │   └── service/
        │       └── trigger/
        │           ├── api/          # API 控制器
        │           ├── consumer/     # MQ 消费者
        │           └── task/         # 定时任务
        └── resources/
            ├── application.yml
            └── mapper/
```

### 2.3 目录结构说明

| 目录 | 职责 | 说明 |
|-----|------|------|
| `common` | 公共部分 | 配置类、常量、枚举、工具类等 |
| `domain` | 领域层 | 实体类(Entity)、DTO、BO、VO 等数据对象 |
| `mapper` | 数据访问层 | MyBatis Mapper 接口 |
| `manager` | 基础服务层 | 继承 MyBatis-Plus 的 `IService` 和 `ServiceImpl`，提供基础 CRUD |
| `application` | 应用服务层 | 复杂业务逻辑编排，组合多个 Manager |
| `trigger` | 触发层 | 外部接口接入：HTTP API、MQ Consumer、定时任务 |

---

## 3. 模块配置变更

### 3.1 mall 父 POM 变更

**文件**: `mall/pom.xml`

#### 3.1.1 模块列表变更
```xml
<!-- 新增子模块 -->
<modules>
    <module>mall-api</module>
    <module>mall-biz</module>
    <!-- 保留现有模块 -->
    <module>mall-template</module>
</modules>
```

#### 3.1.2 依赖管理变更
```xml
<properties>
    <java.version>21</java.version>
    <xxs-common.version>1.0.0</xxs-common.version>
    <revision>1.0.0</revision>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- RuoYi-Cloud-Plus BOM -->
        <dependency>
            <groupId>com.bigbear</groupId>
            <artifactId>xxs-spring-ruoyi-bom</artifactId>
            <version>${xxs-common.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3.2 mall-api POM 配置

**文件**: `mall/mall-api/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.bigbear</groupId>
        <artifactId>mall</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>mall-api</artifactId>
    <packaging>jar</packaging>
    <description>mall 接口模块</description>
    
    <dependencies>
        <!-- RuoYi Common Core -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>
        
        <!-- RuoYi Common Excel -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-excel</artifactId>
        </dependency>
        
        <!-- RuoYi Common Mybatis -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 3.3 mall-biz POM 配置

**文件**: `mall/mall-biz/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.bigbear</groupId>
        <artifactId>mall</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>mall-biz</artifactId>
    <packaging>jar</packaging>
    <description>mall 业务模块</description>
    
    <dependencies>
        <!-- Mall API -->
        <dependency>
            <groupId>com.bigbear</groupId>
            <artifactId>mall-api</artifactId>
            <version>${revision}</version>
        </dependency>
        
        <!-- RuoYi Common Core -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>
        
        <!-- RuoYi Common Mybatis -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>
        
        <!-- RuoYi Common Security -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-security</artifactId>
        </dependency>
        
        <!-- RuoYi Common Service Impl -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-service-impl</artifactId>
        </dependency>
        
        <!-- RuoYi Common Log -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-log</artifactId>
        </dependency>
        
        <!-- RuoYi Common Redis -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-redis</artifactId>
        </dependency>
        
        <!-- RuoYi Common Feign -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-feign</artifactId>
        </dependency>
        
        <!-- RuoYi Common Idempotent -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-idempotent</artifactId>
        </dependency>
        
        <!-- RuoYi Common Encrypt -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-encrypt</artifactId>
        </dependency>
        
        <!-- RuoYi Common Sensitive -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-sensitive</artifactId>
        </dependency>
        
        <!-- RuoYi Common Tenant -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-tenant</artifactId>
        </dependency>
        
        <!-- RuoYi Common Web -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-web</artifactId>
        </dependency>
        
        <!-- RuoYi Common Nacos -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-nacos</artifactId>
        </dependency>
        
        <!-- MyBatis Plus Join -->
        <dependency>
            <groupId>com.github.yulichang</groupId>
            <artifactId>mybatis-plus-join-boot-starter</artifactId>
        </dependency>
        
        <!-- Hutool -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        
        <!-- MapStruct -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
        </dependency>
        
        <!-- RocketMQ -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
        </dependency>
        
        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
        
        <!-- RuoYi API System -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-api-system</artifactId>
        </dependency>
        
        <!-- RuoYi API Resource -->
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-api-resource</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. Order 模块迁移映射

### 4.1 包名映射

| 原 Order 模块 | 新 Mall 模块 | 说明 |
|--------------|-------------|------|
| `com.xxs.order.api` | `com.bigbear.mall.api` | API 模块包名 |
| `com.bigbear.order` | `com.bigbear.mall.biz` | Biz 模块包名 |

### 4.2 文件迁移映射

#### API 模块迁移

| 原文件路径 | 新文件路径 |
|-----------|-----------|
| `order/order-api/src/main/java/com/xxs/order/api/common/enums/GamesEnum.java` | `mall/mall-api/src/main/java/com/bigbear/mall/api/common/enums/GamesEnum.java` |
| `order/order-api/src/main/java/com/xxs/order/api/common/enums/OrderStatusEnum.java` | `mall/mall-api/src/main/java/com/bigbear/mall/api/common/enums/OrderStatusEnum.java` |
| `order/order-api/src/main/java/com/xxs/order/api/common/enums/OrderType.java` | `mall/mall-api/src/main/java/com/bigbear/mall/api/common/enums/OrderType.java` |
| `order/order-api/src/main/java/com/xxs/order/api/common/enums/PayTypeEnum.java` | `mall/mall-api/src/main/java/com/bigbear/mall/api/common/enums/PayTypeEnum.java` |
| `order/order-api/src/main/java/com/xxs/order/api/common/constants/Games.java` | `mall/mall-api/src/main/java/com/bigbear/mall/api/common/constants/Games.java` |

#### Biz 模块迁移

| 原文件路径 | 新文件路径 |
|-----------|-----------|
| `order/order-biz/src/main/java/com/bigbear/order/OrderApplication.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/MallApplication.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/common/GameTypeEnums.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/common/enums/GameTypeEnums.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/entity/po/AccountPO.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/domain/entity/AccountEntity.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/entity/po/OrderPO.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/domain/entity/OrderEntity.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/entity/po/OrderItemPO.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/domain/entity/OrderItemEntity.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/entity/dto/OrderExcelDTO.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/domain/dto/OrderExcelDTO.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/entity/dto/OrderImportDTO.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/domain/dto/OrderImportDTO.java` |
| `order/order-biz/src/main/java/com/bigbear/order/mapper/AccountMapper.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/mapper/AccountMapper.java` |
| `order/order-biz/src/main/java/com/bigbear/order/mapper/orderMapper.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/mapper/OrderMapper.java` |
| `order/order-biz/src/main/java/com/bigbear/order/mapper/OrderItemMapper.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/mapper/OrderItemMapper.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/AccountManager.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/IAccountManager.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/OrderManager.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/IOrderManager.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/OrderItemManager.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/IOrderItemManager.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/impl/AccountManagerImpl.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/impl/AccountManagerImpl.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/impl/orderManagerImpl.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/impl/OrderManagerImpl.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/impl/OrderItemManagerImpl.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/manager/impl/OrderItemManagerImpl.java` |
| `order/order-biz/src/main/java/com/bigbear/order/service/OrderService.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/application/service/OrderService.java` |
| `order/order-biz/src/main/java/com/bigbear/order/service/AutoGameService.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/application/service/AutoGameService.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/controller/OrderController.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/api/OrderController.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/controller/AccountController.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/api/AccountController.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/consumer/OrderConsumer.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/consumer/OrderConsumer.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/consumer/Test.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/consumer/Test.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/consumer/TestConsumer.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/consumer/TestConsumer.java` |
| `order/order-biz/src/main/java/com/bigbear/order/triger/task/MemberTask.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/trigger/task/MemberTask.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/GameTaskSender.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/common/GameTaskSender.java` |
| `order/order-biz/src/main/java/com/bigbear/order/infrastructure/repository/impl/GameTaskSender4YuanShen.java` | `mall/mall-biz/src/main/java/com/bigbear/mall/biz/common/GameTaskSender4YuanShen.java` |

---

## 5. 依赖替换映射

### 5.1 Pig 依赖 → RuoYi-Cloud-Plus 依赖

| Pig 依赖 (移除) | RuoYi-Cloud-Plus 依赖 (新增) | 说明 |
|----------------|---------------------------|------|
| `com.pig4cloud:pig-common-core` | `org.dromara:ruoyi-common-core` | 核心工具类 |
| `com.pig4cloud:pig-common-mybatis` | `org.dromara:ruoyi-common-mybatis` | MyBatis 支持 |
| `com.pig4cloud:pig-common-security` | `org.dromara:ruoyi-common-security` | 安全框架 |
| `com.pig4cloud:pig-common-feign` | `org.dromara:ruoyi-common-feign` | Feign 客户端 |
| `com.pig4cloud:pig-common-log` | `org.dromara:ruoyi-common-log` | 日志框架 |
| `com.pig4cloud:pig-common-swagger` | `org.dromara:ruoyi-common-doc` | API 文档 |
| `com.pig4cloud.excel:excel-spring-boot-starter` | `org.dromara:ruoyi-common-excel` | Excel 处理 |

### 5.2 注解替换映射

| Pig 注解 | RuoYi-Cloud-Plus 注解 | 说明 |
|---------|---------------------|------|
| `@EnablePigDoc` | `@EnableRuoYiDoc` | 文档开启 |
| `@EnablePigResourceServer` | `@EnableRyResourceServer` | 资源服务器 |
| `@PreAuthorize("@pms.hasPermission('xxx')")` | `@SaCheckPermission("xxx")` | 权限检查 |
| `@Inner` | `@InnerAuth` | 内部调用 |

---

## 6. 启动类变更

### 6.1 原 Order 启动类

```java
@EnablePigDoc("order")
@EnablePigResourceServer
@EnableDiscoveryClient
@SpringBootApplication
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

### 6.2 新 Mall 启动类

```java
package com.bigbear.mall.biz;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.dromara.common.resource.server.annotation.EnableRyResourceServer;
import org.dromara.common.swagger.annotation.EnableRyDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Mall 业务模块启动类
 */
@EnableRyDoc("mall")
@EnableRyResourceServer
@EnableDiscoveryClient
@EnableDubbo
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class MallApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MallApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(4096));
        application.run(args);
    }
}
```

---

## 7. 代码迁移示例

### 7.1 枚举类迁移示例

**原 Order 枚举**:
```java
package com.xxs.order.api.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatusEnum {
    PENDING_PAYMENT("待支付"),
    PAID("已支付"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");
    
    private final String desc;
}
```

**新 Mall 枚举**:
```java
package com.bigbear.mall.api.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态枚举
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatusEnum {
    
    PENDING_PAYMENT("pending_payment", "待支付"),
    PAID("paid", "已支付"),
    SHIPPED("shipped", "已发货"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");
    
    private final String code;
    private final String desc;
}
```

### 7.2 Entity 迁移示例

**原 Order PO**:
```java
package com.bigbear.order.infrastructure.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class OrderPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Integer status;
    private LocalDateTime createTime;
}
```

**新 Mall Entity**:
```java
package com.bigbear.mall.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class OrderEntity extends BaseEntity {
    
    /** 订单ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 订单编号 */
    private String orderNo;
    
    /** 用户ID */
    private Long userId;
    
    /** 订单状态 */
    private Integer status;
    
    /** 订单金额 */
    private BigDecimal amount;
    
    /** 支付时间 */
    private LocalDateTime payTime;
    
    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}
```

### 7.3 Manager 层迁移示例

**Manager 接口**（继承 MyBatis-Plus IService）:
```java
package com.bigbear.mall.biz.manager;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bigbear.mall.biz.domain.entity.OrderEntity;

/**
 * 订单基础服务接口
 * 继承 IService 提供基础 CRUD 能力
 */
public interface IOrderManager extends IService<OrderEntity> {
    
    /**
     * 自定义方法：根据订单号查询
     */
    OrderEntity getByOrderNo(String orderNo);
    
    /**
     * 自定义方法：更新订单状态
     */
    boolean updateStatus(Long orderId, Integer status);
}
```

**Manager 实现**（继承 MyBatis-Plus ServiceImpl）:
```java
package com.bigbear.mall.biz.manager.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.manager.IOrderManager;
import com.bigbear.mall.biz.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 订单基础服务实现
 * 继承 ServiceImpl 获得基础 CRUD 实现
 */
@Slf4j
@Service
public class OrderManagerImpl extends ServiceImpl<OrderMapper, OrderEntity> implements IOrderManager {
    
    @Override
    public OrderEntity getByOrderNo(String orderNo) {
        return lambdaQuery()
                .eq(OrderEntity::getOrderNo, orderNo)
                .one();
    }
    
    @Override
    public boolean updateStatus(Long orderId, Integer status) {
        return lambdaUpdate()
                .set(OrderEntity::getStatus, status)
                .eq(OrderEntity::getId, orderId)
                .update();
    }
}
```

### 7.4 Application Service 层示例

**复杂业务编排**:
```java
package com.bigbear.mall.biz.application.service;

import com.bigbear.mall.biz.domain.bo.OrderBo;
import com.bigbear.mall.biz.domain.entity.OrderEntity;
import com.bigbear.mall.biz.domain.entity.OrderItemEntity;
import com.bigbear.mall.biz.manager.IOrderItemManager;
import com.bigbear.mall.biz.manager.IOrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单应用服务
 * 负责复杂业务逻辑编排，组合多个 Manager
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    
    private final IOrderManager orderManager;
    private final IOrderItemManager orderItemManager;
    
    /**
     * 创建订单（复杂业务）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderBo bo) {
        // 1. 保存订单主表
        OrderEntity order = new OrderEntity();
        order.setOrderNo(generateOrderNo());
        order.setUserId(bo.getUserId());
        order.setStatus(0);
        orderManager.save(order);
        
        // 2. 保存订单明细
        List<OrderItemEntity> items = bo.getItems().stream()
                .map(item -> {
                    OrderItemEntity entity = new OrderItemEntity();
                    entity.setOrderId(order.getId());
                    entity.setProductId(item.getProductId());
                    entity.setQuantity(item.getQuantity());
                    return entity;
                })
                .toList();
        orderItemManager.saveBatch(items);
        
        // 3. 发送 MQ 消息
        // ...
        
        return order.getId();
    }
    
    private String generateOrderNo() {
        return "ORDER" + System.currentTimeMillis();
    }
}
```

### 7.5 Controller 迁移示例

**原 Order Controller**:
```java
package com.bigbear.order.triger.controller;

import com.pig4cloud.pig.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order")
public class OrderController {
    
    @GetMapping("/list")
    @PreAuthorize("@pms.hasPermission('order_view')")
    public R list() {
        return R.ok();
    }
}
```

**新 Mall Controller**:
```java
package com.bigbear.mall.biz.trigger.api;

import com.bigbear.mall.biz.application.OrderApplicationService;
import com.bigbear.mall.biz.domain.bo.OrderBo;
import com.bigbear.mall.biz.domain.vo.OrderVo;
import com.bigbear.mall.biz.manager.IOrderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理控制器
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mall/order")
public class OrderController extends BaseController {
    
    private final IOrderManager orderManager;
    private final OrderApplicationService orderApplicationService;
    
    /**
     * 查询订单列表
     */
    @SaCheckPermission("mall:order:list")
    @GetMapping("/list")
    public TableDataInfo list(PageQuery pageQuery) {
        return orderManager.selectPageList(pageQuery);
    }
    
    /**
     * 获取订单详情
     */
    @SaCheckPermission("mall:order:query")
    @GetMapping(value = "/{id}")
    public R<OrderVo> getInfo(@PathVariable Long id) {
        return R.ok(orderManager.getVoById(id));
    }
    
    /**
     * 新增订单（调用 Application Service 处理复杂业务）
     */
    @SaCheckPermission("mall:order:add")
    @Log(title = "订单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody OrderBo bo) {
        Long orderId = orderApplicationService.createOrder(bo);
        return R.ok(orderId);
    }
    
    /**
     * 修改订单
     */
    @SaCheckPermission("mall:order:edit")
    @Log(title = "订单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody OrderBo bo) {
        orderManager.updateByBo(bo);
        return R.ok();
    }
    
    /**
     * 删除订单
     */
    @SaCheckPermission("mall:order:remove")
    @Log(title = "订单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        orderManager.removeByIds(List.of(ids));
        return R.ok();
    }
}
```

### 7.6 DTO/BO/VO 定义示例

**OrderBo.java**:
```java
package com.bigbear.mall.biz.domain.bo;

import lombok.Data;
import java.util.List;

/**
 * 订单业务对象
 */
@Data
public class OrderBo {
    
    private Long id;
    private Long userId;
    private String orderNo;
    private Integer status;
    private List<OrderItemBo> items;
}
```

**OrderVo.java**:
```java
package com.bigbear.mall.biz.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象
 */
@Data
public class OrderVo {
    
    private Long id;
    private String orderNo;
    private Long userId;
    private Integer status;
    private String statusName;
    private BigDecimal amount;
    private LocalDateTime createTime;
    private List<OrderItemVo> items;
}
```

---

## 8. 配置文件

### 8.1 mall-biz application.yml

```yaml
server:
  port: 9200

spring:
  application:
    name: mall-biz
  profiles:
    active: @profiles.active@
  main:
    allow-circular-references: true
    allow-bean-definition-overriding: true
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

# MyBatis Plus 配置
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  type-aliases-package: com.bigbear.mall.biz.domain.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: true
    lazy-loading-enabled: true
    multiple-result-sets-enabled: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
    db-config:
      id-type: auto
      logic-delete-field: delFlag
      logic-delete-value: 2
      logic-not-delete-value: 0

# 日志配置
logging:
  level:
    com.bigbear.mall: debug
    org.dromara: debug
```

---

## 9. 迁移检查清单

### 9.1 前置条件
- [ ] 备份现有 `order` 模块代码
- [ ] 确认 `mall` 父 POM 已配置 RuoYi-Cloud-Plus BOM
- [ ] 确认 `xxs-spring-ruoyi-bom` 版本与父项目一致

### 9.2 API 模块检查项
- [ ] 创建 `mall-api` 目录结构
- [ ] 配置 `mall-api/pom.xml` 依赖
- [ ] 迁移所有枚举类到 `common/enums`
- [ ] 迁移所有常量到 `common/constants`
- [ ] 更新包名为 `com.bigbear.mall.api`

### 9.3 Biz 模块检查项
- [ ] 创建 `mall-biz` 目录结构
- [ ] 配置 `mall-biz/pom.xml` 依赖（移除 AI 相关依赖）
- [ ] 创建启动类 `MallApplication`
- [ ] 迁移 Entity 到 `domain/entity`
- [ ] 迁移 DTO 到 `domain/dto`
- [ ] 迁移 BO 到 `domain/bo`
- [ ] 迁移 VO 到 `domain/vo`
- [ ] 创建 Manager 层（IService/ServiceImpl）到 `manager`
- [ ] 迁移 Mapper 到 `mapper`
- [ ] 迁移复杂业务 Service 到 `application/service`
- [ ] 迁移 Controller 到 `trigger/api`
- [ ] 迁移 Consumer 到 `trigger/consumer`
- [ ] 迁移 Task 到 `trigger/task`
- [ ] 更新所有 Pig 注解为 RuoYi 注解
- [ ] 更新返回类型 `R` 为 `org.dromara.common.core.domain.R`
- [ ] 配置 `application.yml`
- [ ] 迁移 Mapper XML 文件

### 9.4 验证检查项
- [ ] 执行 `mvn clean compile` 编译成功
- [ ] 执行 `mvn clean package` 打包成功
- [ ] 启动服务无异常
- [ ] API 接口调用正常
- [ ] 数据库操作正常

---

## 10. 风险与注意事项

### 10.1 潜在风险
1. **包名变更**: 涉及 Feign 调用的服务需要同步更新包名引用
2. **注解差异**: Pig 和 RuoYi 的权限注解机制不同，需全面检查
3. **返回类型**: `R` 类的包路径不同，需统一替换
4. **基础实体类**: `BaseEntity` 的字段可能存在差异

### 10.2 兼容性处理
1. 保留原 `order` 模块直至 `mall` 模块验证通过
2. 数据库表结构保持不变，仅调整实体类包名
3. 接口 URL 路径建议保持一致或做好网关路由映射

---

## 11. 附录

### 11.1 参考文档
- [RuoYi-Cloud-Plus 官方文档](https://gitee.com/dromara/RuoYi-Cloud-Plus)
- [Pig 官方文档](https://gitee.com/log4j/pig)

### 11.2 相关模块版本
| 组件 | 版本 |
|-----|------|
| RuoYi-Cloud-Plus | 2.5.2 |
| Spring Boot | 3.2.x |
| Spring Cloud | 2023.0.x |
| Spring Cloud Alibaba | 2022.0.x |
| MyBatis Plus | 3.5.x |
| Java | 21 |

---

**变更提案版本**: v1.0  
**创建日期**: 2025-02-09  
**提案人**: AI Assistant  
**状态**: 待评审
