# Snail-Job 多语言接入方案（Spring Boot / Quarkus / Go-Kratos）

> 适用工程：java/xxs-micro（Spring Boot + Quarkus）、go/bear-admin（Go-Kratos）
> 参考范式：D:\xhf\project\test\ruoyi\RuoYi-Cloud-Plus
> 官方文档：https://snailjob.opensnail.com/docs/guide/client/go/go.html
> 选定版本：1.9.0（2025-12-21 发布，Central Repository 已确认）

## 1. 选型结论

当前服务栈同时包含 Spring Boot、Quarkus、Go-Kratos 三类框架。

- xxl-job：Java/Spring 生态优先，Go 执行器社区版停滞在 2022 年，跨语言需补丁式支持，不契合多语言并存。
- snail-job（Apache-2.0，aizuda 团队）：从设计上支持多语言客户端（Java / Go / Python / Solon 等），提供统一调度、重试、工作流、死信、告警，且原生支持集群/广播/分片/MapReduce/DAG。

结论：选 snail-job，三端共用同一 server，配置与概念统一。

## 2. 总体架构

```
                Snail-Job Server (1.9.0)
                netty gRPC :17888  /  web 控制台 :8080 (/snail-job)
                            |
        +-------------------+--------------------+
        |                   |                    |
 Spring Boot 客户端    Quarkus 客户端       Go-Kratos 客户端
 (xxs-spring-job)    (xxs-quarkus-job)      (bear-admin/job)
  group: xxs_spring   group: xxs_quarkus     group: bear_admin_group
```

每个端向 server 注册自己的 group，在 server 后台为该 group 下创建任务并选择执行器名称即可调度。

## 3. 坐标与版本汇总

| 端 | 坐标 / 路径 | 版本 |
|---|---|---|
| Java starter | com.aizuda:snail-job-client-starter | 1.9.0 |
| Java job-core | com.aizuda:snail-job-client-job-core | 1.9.0 |
| Java retry-core（按需） | com.aizuda:snail-job-client-retry-core | 1.9.0 |
| Go module | github.com/open-snail/snail-job-go | 1.9.0 |

版本通过 xxs-dependencies 的 snailjob.version 统一管理（Spring 侧）；Go 侧执行 go get github.com/open-snail/snail-job-go@v1.9.0。

## 4. 服务端部署（二选一，需确认）

方案 A：直接使用官方 Docker 镜像（推荐先打通链路）
```bash
docker run -d --name snail-job \
  -p 17888:17888 -p 8080:8080 \
  -v /opt/snail-job/data:/snail-job/data \
  -v /opt/snail-job/conf:/snail-job/conf \
  aizuda/snail-job:1.9.0
```
需准备官方 snail_job.sql（建库建表），并在 application.yml 配置数据源。

方案 B：mirror RuoYi 的 ruoyi-snailjob-server 模块到 java/xxs-micro/xxs-snailjob-server
- 依赖 snail-job-server-starter（servlet，context-path /snail-job）。
- 参考 ruoyi-visual/ruoyi-snailjob-server 的 pom.xml（含 scala-library 排除）与 application.yml。
- 同样需初始化官方 snail_job.sql。

> 建议：先用方案 A 拉起 server 打通三端链路，验证无误后再视情况迁移到方案 B 纳管进本仓库。

## 5. 关键约定（三端通用，务必遵守）

1. **namespace**：
   - Java 客户端 `snail-job.namespace` 填**命名空间名称**（如 `public`）。
   - Go 客户端 `Namespace` 填 `sj_namespace` 表的 **unique_id**（一长串 hex，默认空间为 `764d604ec6fc45f68cd92514c40e9e1a`）。
   - 两者指向同一空间但取值含义不同，最易踩坑。
2. **端口一致性**：客户端 `server.port` 必须与服务端 netty 端口一致（本方案统一 `17888`，非默认 `17890`）。
3. **token**：三端一致，建议使用 server 分配的 `SJ_xxxx` 令牌。
4. **group**：每个端唯一，server 后台需先建好对应 group 才能正确分派任务。
5. **客户端端口**：`snail-job.port` 默认 17889；Spring 侧随主端口飘移（`2${server.port}`），确保多实例不冲突。

## 6. Spring Boot 端接入（java/xxs-micro）

参考 RuoYi 的 `ruoyi-common/ruoyi-common-job` 模块风格：在 `xxs-common-spring` 下新建公共模块 `xxs-spring-job`，业务应用按需引入。

### 6.1 版本统一管理（xxs-dependencies/pom.xml）

在 `<properties>` 增加：
```xml
<snailjob.version>1.9.0</snailjob.version>
```
在 `<dependencyManagement>` 增加：
```xml
<dependency>
    <groupId>com.aizuda</groupId>
    <artifactId>snail-job-client-starter</artifactId>
    <version>${snailjob.version}</version>
</dependency>
<dependency>
    <groupId>com.aizuda</groupId>
    <artifactId>snail-job-client-job-core</artifactId>
    <version>${snailjob.version}</version>
</dependency>
```

### 6.2 公共模块 xxs-spring-job/pom.xml

```xml
<parent>
    <groupId>com.bigbear</groupId>
    <artifactId>xxs-common-spring</artifactId>
    <version>${revision}</version>
</parent>
<artifactId>xxs-spring-job</artifactId>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
        <groupId>com.aizuda</groupId>
        <artifactId>snail-job-client-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.aizuda</groupId>
        <artifactId>snail-job-client-job-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

### 6.3 自动配置（仿 RuoYi SnailJobConfig）

`xxs-spring-job/src/main/java/com/bigbear/job/config/SnailJobAutoConfig.java`
```java
package com.bigbear.job.config;

import com.aizuda.snailjob.client.starter.EnableSnailJob;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SnailJobProperties.class)
@EnableScheduling
@EnableSnailJob
public class SnailJobAutoConfig {
}
```

`xxs-spring-job/src/main/java/com/bigbear/job/config/SnailJobProperties.java`
```java
package com.bigbear.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snail-job")
public class SnailJobProperties {
    private boolean enabled;
    private String group;
    private String token;
    private String namespace;
    private String host;
    private Integer port;
    private final Server server = new Server();

    // getters/setters ...

    public static class Server {
        private String host;
        private String port;
        // getters/setters ...
    }
}
```

`xxs-spring-job/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
com.bigbear.job.config.SnailJobAutoConfig
```

### 6.4 业务应用配置（application.yml）

```yaml
snail-job:
  enabled: true
  group: "xxs_spring_group"
  token: "SJ_xxxx"
  namespace: public
  server:
    host: 127.0.0.1
    port: 17888
  host: ""
  port: 2${server.port}
```

### 6.5 任务类（仿 RuoYi TestAnnoJobExecutor）

```java
package com.bigbear.xxx.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import org.springframework.stereotype.Component;

@Component
@JobExecutor(name = "demoJobExecutor")
public class DemoJobExecutor {
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        // 业务逻辑
        return ExecuteResult.success("ok");
    }
}
```

> 新建模块后，需在 `xxs-common-spring/pom.xml` 的 `<modules>` 中登记 `xxs-spring-job`。

## 7. Quarkus 端接入（java/xxs-micro）

Quarkus 不是 Spring，不能使用 starter 的 `@EnableSnailJob` / `@AutoConfiguration`。改为在 `xxs-common-quarkus` 下新建 `xxs-quarkus-job` 模块，仅依赖 `snail-job-client-job-core`，手动构建客户端并绑定 Quarkus 生命周期。

### 7.1 xxs-quarkus-job/pom.xml

```xml
<parent>
    <groupId>com.bigbear</groupId>
    <artifactId>xxs-common-quarkus</artifactId>
    <version>${revision}</version>
</parent>
<artifactId>xxs-quarkus-job</artifactId>

<dependencies>
    <dependency>
        <groupId>com.aizuda</groupId>
        <artifactId>snail-job-client-job-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
</dependencies>
```

### 7.2 生命周期绑定（@Observes StartupEvent / ShutdownEvent）

> 实现契约：Spring starter 中 `SnailJobBeanPostProcessor` 负责扫描 `@JobExecutor` 并 `addJobExecutor`。core 包本身不含该扫描，因此 **Quarkus 端须在 `SnailJobLifecycle` 中显式 `addJobExecutor("name", executorInstance)`**。下面给出的 `SnailJobClientBuilder` / `addJobExecutor` / `start` / `stop` 为基于 core 包入口的骨架，**类名与方法名需按实际 jar 核准**（建议 clone `github.com/open-snail/snail-job-go` 的 Java 同组织仓库或反编译 `snail-job-client-job-core` jar 确认 `SnailJobClient` 的真实构造 API）。

`xxs-quarkus-job/src/main/java/com/bigbear/job/SnailJobLifecycle.java`
```java
package com.bigbear.job;

import com.aizuda.snailjob.client.job.core.SnailJobClientBuilder; // 以实际包为准
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SnailJobLifecycle {

    private Object client; // 实际类型为 core 包的 SnailJobClient

    void onStart(@Observes StartupEvent ev) {
        client = SnailJobClientBuilder.newClient()
                .setServerHost("127.0.0.1")
                .setServerPort("17888")
                .setGroupName("xxs_quarkus_group")
                .setNamespace("public")
                .setToken("SJ_xxxx")
                .addJobExecutor("demoQuarkusJobExecutor", new DemoQuarkusJobExecutor())
                .build();
        // 启动（具体方法名以 core 包为准，可能为 start()）
        // ((SnailJobClient) client).start();
    }

    void onStop(@Observes ShutdownEvent ev) {
        // ((SnailJobClient) client).stop();
    }
}
```

`xxs-quarkus-job/src/main/java/com/bigbear/job/DemoQuarkusJobExecutor.java`
```java
package com.bigbear.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.model.dto.ExecuteResult;

@JobExecutor(name = "demoQuarkusJobExecutor")
public class DemoQuarkusJobExecutor {
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        // 业务逻辑
        return ExecuteResult.success("ok");
    }
}
```

### 7.3 Quarkus 配置（application.yml）

```yaml
snail-job:
  enabled: true
  group: "xxs_quarkus_group"
  token: "SJ_xxxx"
  namespace: public
  server:
    host: 127.0.0.1
    port: 17888
  host: ""
  port: 17889
```

## 8. Go 端接入（go/bear-admin，Go-Kratos）

参考官方 Go 文档。Go module 路径：`github.com/open-snail/snail-job-go`。

### 8.1 引入依赖

```bash
cd go/bear-admin
go get github.com/open-snail/snail-job-go@v1.9.0
```

### 8.2 客户端初始化

`go/bear-admin/app/ai/service/internal/job/job.go`
```go
package job

import (
	"github.com/open-snail/snail-job-go/snailjob"
	"github.com/open-snail/snail-job-go/snailjob/dto"
	"github.com/open-snail/snail-job-go/snailjob/job"
	"github.com/sirupsen/logrus"
)

// InitSnailJob 创建并初始化 SnailJob 客户端，返回管理器用于优雅停机
func InitSnailJob() *snailjob.SnailJobManager {
	exec := snailjob.NewSnailJobManager(&dto.Options{
		ServerHost:   "127.0.0.1",
		ServerPort:   "17888",
		HostIP:       "0.0.0.0",
		HostPort:     "17889",
		Namespace:    "<sj_namespace 表的 unique_id，默认空间为 764d604ec6fc45f68cd92514c40e9e1a>",
		GroupName:    "bear_admin_group",
		Token:        "SJ_xxxx",
		Level:        logrus.InfoLevel,
		ReportCaller: true,
	})

	exec.Register("testJobExecutor", func() job.IJobExecutor {
		return &TestJobExecutor{}
	})

	if nil == exec.Init() {
		exec.Run()
	}
	return exec
}
```

`go/bear-admin/app/ai/service/internal/job/test_executor.go`
```go
package job

import (
	"github.com/open-snail/snail-job-go/snailjob/dto"
	"github.com/open-snail/snail-job-go/snailjob/job"
)

// TestJobExecutor 普通定时任务执行器
type TestJobExecutor struct {
	job.BaseJobExecutor
}

func (executor *TestJobExecutor) DoJobExecute(jobArgs dto.IJobArgs) dto.ExecuteResult {
	// 业务代码；如需工作流上下文用 jobArgs.GetWfContext()
	return *dto.Success().WithMessage("hello 这是 go 客户端")
}
```

### 8.3 挂接 Kratos 生命周期

在 `app/ai/service/cmd/server/main.go` 的 `runApp()` 中，于 `bootstrap.RunApp` 之前启动客户端，并 `defer exec.Stop()` 实现优雅停机：

```go
func runApp() {
	// ... 现有初始化 ...

	exec := job.InitSnailJob()
	defer exec.Stop()

	bootstrap.RunApp(...)
}
```

> 注意：`exec.Run()` 在官方文档中为**阻塞式**（启动后持续接收调度）。若 `runApp` 后续逻辑需在 `Run()` 之后执行，则改为 `go exec.Run()` 并利用信号监听保持进程存活；若 `bootstrap.RunApp` 本身已阻塞，则直接 `exec.Run()` 后 `defer exec.Stop()` 即可。

## 9. 验证步骤

1. 启动 server（方案 A 或 B），访问 `http://127.0.0.1:8080/snail-job` 控制台。
2. 在「组管理」分别创建 `xxs_spring_group`、`xxs_quarkus_group`、`bear_admin_group`（命名空间选 `public`）。
3. 逐个启动三端应用，控制台「客户端管理」应能看到对应 `host-id` 在线（Go 端形如 `go-xxxxxx`）。
4. 各端新建任务（选择对应 group 与执行器名 `demoJobExecutor` / `demoQuarkusJobExecutor` / `testJobExecutor`），手动触发一次，观察执行结果与实时日志。
5. 建议**先接 Go 端**打通链路（依赖最少、最易验证），再补 Spring Boot、Quarkus。

## 10. 待确认事项

1. **服务端部署**：选 A（官方镜像，快）还是 B（mirror 进仓库，便于纳管）？
2. **先接哪一端**：建议 Go 端先行，确认后按第 9 节逐步补齐。
3. **Quarkus core 包 API**：第 7.2 节的 `SnailJobClientBuilder`/`addJobExecutor`/`start`/`stop` 需在落地时用实际 jar 核准类名与方法签名（core 包非 Spring，不提供 `@JobExecutor` 自动扫描，必须显式注册）。
4. **namespace 取值**：Go 端请填入 `sj_namespace` 表的 `unique_id`，Java 端填命名空间名称（默认 `public` 或默认空间 unique_id，两端需指向同一空间）。
