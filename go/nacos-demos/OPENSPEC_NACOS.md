# OpenSpec 提案: Kratos 项目集成 Nacos 服务注册发现与配置管理

## 1. 概述

### 1.1 目标
将当前 Kratos 项目改造为使用 Kratos 官方 Nacos 包实现:
- 服务注册与发现
- 配置中心集成 (从 Nacos 拉取配置)

### 1.2 参考实现
- `template/server.go` - Nacos 服务端注册示例
- `template/client.go` - Nacos 客户端发现示例

### 1.3 依赖包
项目已包含所需依赖:
```
github.com/go-kratos/kratos/contrib/registry/nacos/v2 v2.0.0
github.com/nacos-group/nacos-sdk-go v1.1.6
```

---

## 2. 配置文件改造

### 2.1 更新 `internal/conf/conf.proto`

在 `conf.proto` 中添加 Nacos 配置结构:

```protobuf
syntax = "proto3";
package kratos.api;

option go_package = "demos/internal/conf;conf";

import "google/protobuf/duration.proto";

message Bootstrap {
  Server server = 1;
  Data data = 2;
  Registry registry = 3;  // 新增: 服务注册中心配置
}

message Server {
  message HTTP {
    string network = 1;
    string addr = 2;
    google.protobuf.Duration timeout = 3;
  }
  message GRPC {
    string network = 1;
    string addr = 2;
    google.protobuf.Duration timeout = 3;
  }
  HTTP http = 1;
  GRPC grpc = 2;
}

message Data {
  message Database {
    string driver = 1;
    string source = 2;
  }
  message Redis {
    string network = 1;
    string addr = 2;
    google.protobuf.Duration read_timeout = 3;
    google.protobuf.Duration write_timeout = 4;
  }
  Database database = 1;
  Redis redis = 2;
}

// 新增: Registry 配置
message Registry {
  message Nacos {
    message ServerConfig {
      string ip_addr = 1;           // Nacos 服务器 IP
      uint64 port = 2;              // Nacos 服务器端口
      string scheme = 3;            // 协议: http/https
      string context_path = 4;      // 上下文路径
      uint64 grpc_port = 5;         // gRPC 端口
    }
    message ClientConfig {
      string namespace_id = 1;      // 命名空间 ID
      uint64 timeout_ms = 2;        // 超时时间(毫秒)
      bool not_load_cache_at_start = 3;  // 启动时不加载缓存
      string log_dir = 4;           // 日志目录
      string cache_dir = 5;         // 缓存目录
      string log_level = 6;         // 日志级别: debug/info/warn/error
      string username = 7;          // 用户名(鉴权)
      string password = 8;          // 密码(鉴权)
      string endpoint = 9;          // 端点(用于地址服务器)
      string region_id = 10;        // 地域 ID
      string access_key = 11;       // AccessKey(阿里云)
      string secret_key = 12;       // SecretKey(阿里云)
      bool open_kms = 13;           // 是否开启 KMS
      string rotate_time = 14;      // 日志轮转时间
      int32 max_age = 15;           // 日志最大保留天数
    }
    repeated ServerConfig server = 1;
    ClientConfig client = 2;
  }
  Nacos nacos = 1;
}
```

**注意**: 修改 `conf.proto` 后需要重新生成 `conf.pb.go` (通过 `make config`)

---

### 2.2 更新 `configs/config.yaml`

添加 Nacos 配置:

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 1s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 1s

data:
  database:
    driver: mysql
    source: root:root@tcp(127.0.0.1:3306)/test?parseTime=True&loc=Local
  redis:
    addr: 127.0.0.1:6379
    read_timeout: 0.2s
    write_timeout: 0.2s

# 新增: Nacos 服务注册发现与配置中心
registry:
  nacos:
    # Nacos 服务器配置 (支持集群配置)
    server:
      - ip_addr: 127.0.0.1       # Nacos 服务器地址
        port: 8848                # HTTP 端口
        scheme: http              # 协议: http 或 https
        context_path: /nacos      # 上下文路径 (可选)
        grpc_port: 9848           # gRPC 端口 (可选，Nacos 2.x)
    
    # Nacos 客户端配置
    client:
      namespace_id: public        # 命名空间 ID (用于环境隔离)
      timeout_ms: 5000            # 请求超时时间(毫秒)
      not_load_cache_at_start: true  # 启动时不加载缓存
      log_dir: ./tmp/nacos/log    # 日志目录
      cache_dir: ./tmp/nacos/cache  # 缓存目录
      log_level: info             # 日志级别: debug/info/warn/error
      rotate_time: 1h             # 日志轮转时间
      max_age: 3                  # 日志最大保留天数
      # username: nacos           # 用户名 (开启鉴权时配置)
      # password: nacos           # 密码 (开启鉴权时配置)
      # endpoint: ""              # 端点 (使用地址服务器时配置)
      # region_id: ""             # 地域 ID (阿里云)
      # access_key: ""            # AccessKey (阿里云 KMS)
      # secret_key: ""            # SecretKey (阿里云 KMS)
      # open_kms: false           # 是否开启 KMS 加密
```

---

## 3. 代码改造

### 3.1 新增 `internal/registry/nacos.go`

创建 Nacos 注册中心初始化模块:

```go
package registry

import (
	"demos/internal/conf"
	
	"github.com/go-kratos/kratos/contrib/registry/nacos/v2"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/registry"
	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

// NewNacosRegistry 创建 Nacos 服务注册中心
// 所有配置参数从 conf.Registry 读取，无硬编码
func NewNacosRegistry(c *conf.Registry, logger log.Logger) (registry.Registrar, error) {
	if c == nil || c.Nacos == nil {
		log.Info("Nacos registry config is nil, skipping registry")
		return nil, nil
	}
	
	// 构建 ServerConfig (从配置文件读取)
	serverConfigs := make([]constant.ServerConfig, 0, len(c.Nacos.Server))
	for _, s := range c.Nacos.Server {
		sc := constant.ServerConfig{
			IpAddr:      s.IpAddr,
			Port:        s.Port,
			Scheme:      s.Scheme,
			ContextPath: s.ContextPath,
			GrpcPort:    s.GrpcPort,
		}
		serverConfigs = append(serverConfigs, sc)
	}
	
	// 构建 ClientConfig (从配置文件读取)
	cc := c.Nacos.Client
	clientConfig := constant.ClientConfig{
		NamespaceId:         cc.NamespaceId,
		TimeoutMs:           cc.TimeoutMs,
		NotLoadCacheAtStart: cc.NotLoadCacheAtStart,
		LogDir:              cc.LogDir,
		CacheDir:            cc.CacheDir,
		LogLevel:            cc.LogLevel,
		Username:            cc.Username,
		Password:            cc.Password,
		Endpoint:            cc.Endpoint,
		RegionId:            cc.RegionId,
		AccessKey:           cc.AccessKey,
		SecretKey:           cc.SecretKey,
		OpenKMS:             cc.OpenKms,
		RotateTime:          cc.RotateTime,
		MaxAge:              int64(cc.MaxAge),
	}
	
	// 创建 Nacos NamingClient
	client, err := clients.NewNamingClient(
		vo.NacosClientParam{
			ClientConfig:  &clientConfig,
			ServerConfigs: serverConfigs,
		},
	)
	if err != nil {
		log.Errorf("failed to create nacos naming client: %v", err)
		return nil, err
	}
	
	log.Infof("Nacos registry initialized successfully, servers: %v, namespace: %s", 
		serverConfigs, clientConfig.NamespaceId)
	return nacos.New(client), nil
}
```

---

### 3.2 新增 `internal/registry/registry.go`

创建 ProviderSet:

```go
package registry

import "github.com/google/wire"

// ProviderSet is registry providers.
var ProviderSet = wire.NewSet(NewNacosRegistry)
```

---

### 3.3 更新 `cmd/demos/main.go`

修改 `newApp` 函数，添加服务注册支持:

```go
package main

import (
	"flag"
	"os"

	"demos/internal/conf"

	"github.com/go-kratos/kratos/v2"
	"github.com/go-kratos/kratos/v2/config"
	"github.com/go-kratos/kratos/v2/config/file"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/middleware/tracing"
	"github.com/go-kratos/kratos/v2/registry"
	"github.com/go-kratos/kratos/v2/transport/grpc"
	"github.com/go-kratos/kratos/v2/transport/http"

	_ "go.uber.org/automaxprocs"
)

// go build -ldflags "-X main.Version=x.y.z"
var (
	// Name is the name of the compiled software.
	Name string = "demos"  // 设置默认服务名
	// Version is the version of the compiled software.
	Version string = "v1.0.0"  // 设置默认版本
	// flagconf is the config flag.
	flagconf string

	id, _ = os.Hostname()
)

func init() {
	flag.StringVar(&flagconf, "conf", "../../configs", "config path, eg: -conf config.yaml")
}

// 修改 newApp 函数，添加 registrar 参数
func newApp(logger log.Logger, gs *grpc.Server, hs *http.Server, rr registry.Registrar) *kratos.App {
	opts := []kratos.Option{
		kratos.ID(id),
		kratos.Name(Name),
		kratos.Version(Version),
		kratos.Metadata(map[string]string{}),
		kratos.Logger(logger),
		kratos.Server(
			gs,
			hs,
		),
	}
	
	// 如果 registrar 不为空，添加服务注册
	if rr != nil {
		opts = append(opts, kratos.Registrar(rr))
	}
	
	return kratos.New(opts...)
}

func main() {
	flag.Parse()
	logger := log.With(log.NewStdLogger(os.Stdout),
		"ts", log.DefaultTimestamp,
		"caller", log.DefaultCaller,
		"service.id", id,
		"service.name", Name,
		"service.version", Version,
		"trace.id", tracing.TraceID(),
		"span.id", tracing.SpanID(),
	)
	c := config.New(
		config.WithSource(
			file.NewSource(flagconf),
		),
	)
	defer c.Close()

	if err := c.Load(); err != nil {
		panic(err)
	}

	var bc conf.Bootstrap
	if err := c.Scan(&bc); err != nil {
		panic(err)
	}

	// 修改 wireApp 调用，传入 Registry 配置
	app, cleanup, err := wireApp(bc.Server, bc.Data, bc.Registry, logger)
	if err != nil {
		panic(err)
	}
	defer cleanup()

	// start and wait for stop signal
	if err := app.Run(); err != nil {
		panic(err)
	}
}
```

---

### 3.4 更新 `cmd/demos/wire.go`

更新 Wire 依赖注入配置:

```go
//go:build wireinject
// +build wireinject

// The build tag makes sure the stub is not built in the final build.

package main

import (
	"demos/internal/biz"
	"demos/internal/conf"
	"demos/internal/data"
	"demos/internal/registry"  // 新增
	"demos/internal/server"
	"demos/internal/service"

	"github.com/go-kratos/kratos/v2"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/google/wire"
)

// wireApp init kratos application.
func wireApp(*conf.Server, *conf.Data, *conf.Registry, log.Logger) (*kratos.App, func(), error) {
	panic(wire.Build(
		server.ProviderSet, 
		data.ProviderSet, 
		biz.ProviderSet, 
		service.ProviderSet, 
		registry.ProviderSet,  // 新增
		newApp,
	))
}
```

**注意**: 修改 `wire.go` 后需要重新生成 `wire_gen.go`:
```bash
cd cmd/demos
wire
```

---

## 4. Nacos 配置中心集成 (可选增强)

### 4.1 扩展 `internal/conf/conf.proto`

在 Registry 配置中添加配置中心相关字段:

```protobuf
// Registry 配置 (包含服务注册发现和配置中心)
message Registry {
  message Nacos {
    message ServerConfig {
      string ip_addr = 1;
      uint64 port = 2;
      string scheme = 3;
      string context_path = 4;
      uint64 grpc_port = 5;
    }
    message ClientConfig {
      string namespace_id = 1;
      uint64 timeout_ms = 2;
      bool not_load_cache_at_start = 3;
      string log_dir = 4;
      string cache_dir = 5;
      string log_level = 6;
      string username = 7;
      string password = 8;
      string endpoint = 9;
      string region_id = 10;
      string access_key = 11;
      string secret_key = 12;
      bool open_kms = 13;
      string rotate_time = 14;
      int32 max_age = 15;
    }
    // 配置中心相关配置
    message ConfigConfig {
      string group = 1;           // 配置组，默认: DEFAULT_GROUP
      string data_id = 2;         // 配置 DataId，如: demos.yaml
      bool enable = 3;            // 是否启用配置中心
    }
    repeated ServerConfig server = 1;
    ClientConfig client = 2;
    ConfigConfig config = 3;      // 新增配置中心配置
  }
  Nacos nacos = 1;
}
```

### 4.2 更新 `configs/config.yaml`

添加配置中心相关配置:

```yaml
registry:
  nacos:
    server:
      - ip_addr: 127.0.0.1
        port: 8848
        scheme: http
        context_path: /nacos
        grpc_port: 9848
    client:
      namespace_id: public
      timeout_ms: 5000
      not_load_cache_at_start: true
      log_dir: ./tmp/nacos/log
      cache_dir: ./tmp/nacos/cache
      log_level: info
      rotate_time: 1h
      max_age: 3
    # 配置中心配置 (可选)
    config:
      enable: false               # 是否启用 Nacos 配置中心
      group: DEFAULT_GROUP        # 配置组
      data_id: demos.yaml         # 配置 DataId
```

### 4.3 创建 `internal/config/nacos.go`

实现从 Nacos 配置中心拉取配置 (从配置文件读取参数):

```go
package config

import (
	"demos/internal/conf"
	
	"github.com/go-kratos/kratos/contrib/config/nacos/v2"
	"github.com/go-kratos/kratos/v2/config"
	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

// NewNacosConfigSource 创建 Nacos 配置源
// 所有参数从 conf.Registry 读取
func NewNacosConfigSource(c *conf.Registry) (config.Source, error) {
	if c == nil || c.Nacos == nil || c.Nacos.Config == nil || !c.Nacos.Config.Enable {
		return nil, nil
	}
	
	// 构建 ServerConfig (从配置文件读取)
	serverConfigs := make([]constant.ServerConfig, 0, len(c.Nacos.Server))
	for _, s := range c.Nacos.Server {
		sc := constant.ServerConfig{
			IpAddr:      s.IpAddr,
			Port:        s.Port,
			Scheme:      s.Scheme,
			ContextPath: s.ContextPath,
			GrpcPort:    s.GrpcPort,
		}
		serverConfigs = append(serverConfigs, sc)
	}
	
	// 构建 ClientConfig (从配置文件读取)
	cc := c.Nacos.Client
	clientConfig := &constant.ClientConfig{
		NamespaceId:         cc.NamespaceId,
		TimeoutMs:           cc.TimeoutMs,
		NotLoadCacheAtStart: cc.NotLoadCacheAtStart,
		LogDir:              cc.LogDir,
		CacheDir:            cc.CacheDir,
		LogLevel:            cc.LogLevel,
		Username:            cc.Username,
		Password:            cc.Password,
		Endpoint:            cc.Endpoint,
		RegionId:            cc.RegionId,
		AccessKey:           cc.AccessKey,
		SecretKey:           cc.SecretKey,
		OpenKMS:             cc.OpenKms,
		RotateTime:          cc.RotateTime,
		MaxAge:              int64(cc.MaxAge),
	}
	
	// 创建 Nacos ConfigClient
	client, err := clients.NewConfigClient(
		vo.NacosClientParam{
			ClientConfig:  clientConfig,
			ServerConfigs: serverConfigs,
		},
	)
	if err != nil {
		return nil, err
	}
	
	// 从配置文件读取 Group 和 DataId
	opts := []nacos.Option{
		nacos.WithGroup(c.Nacos.Config.Group),
		nacos.WithDataID(c.Nacos.Config.DataId),
	}
	
	return nacos.NewConfigSource(client, opts...), nil
}
```

### 4.4 修改 `cmd/demos/main.go` 支持 Nacos 配置

```go
package main

import (
	"flag"
	"os"

	"demos/internal/conf"
	"demos/internal/config"  // 新增

	"github.com/go-kratos/kratos/v2"
	kratosConfig "github.com/go-kratos/kratos/v2/config"
	"github.com/go-kratos/kratos/v2/config/file"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/middleware/tracing"
	"github.com/go-kratos/kratos/v2/registry"
	"github.com/go-kratos/kratos/v2/transport/grpc"
	"github.com/go-kratos/kratos/v2/transport/http"

	_ "go.uber.org/automaxprocs"
)

var (
	Name     string = "demos"
	Version  string = "v1.0.0"
	flagconf string
	id, _    = os.Hostname()
)

func init() {
	flag.StringVar(&flagconf, "conf", "../../configs", "config path, eg: -conf config.yaml")
}

func newApp(logger log.Logger, gs *grpc.Server, hs *http.Server, rr registry.Registrar) *kratos.App {
	opts := []kratos.Option{
		kratos.ID(id),
		kratos.Name(Name),
		kratos.Version(Version),
		kratos.Metadata(map[string]string{}),
		kratos.Logger(logger),
		kratos.Server(gs, hs),
	}
	
	if rr != nil {
		opts = append(opts, kratos.Registrar(rr))
	}
	
	return kratos.New(opts...)
}

func main() {
	flag.Parse()
	logger := log.With(log.NewStdLogger(os.Stdout),
		"ts", log.DefaultTimestamp,
		"caller", log.DefaultCaller,
		"service.id", id,
		"service.name", Name,
		"service.version", Version,
		"trace.id", tracing.TraceID(),
		"span.id", tracing.SpanID(),
	)
	
	// 第一步: 加载本地配置文件
	localConfig := kratosConfig.New(
		kratosConfig.WithSource(file.NewSource(flagconf)),
	)
	if err := localConfig.Load(); err != nil {
		panic(err)
	}
	
	var bootstrap conf.Bootstrap
	if err := localConfig.Scan(&bootstrap); err != nil {
		panic(err)
	}
	
	// 第二步: 检查是否启用 Nacos 配置中心
	var finalConfig kratosConfig.Config
	if bootstrap.Registry != nil && 
	   bootstrap.Registry.Nacos != nil && 
	   bootstrap.Registry.Nacos.Config != nil && 
	   bootstrap.Registry.Nacos.Config.Enable {
		
		// 使用 Nacos 配置中心
		log.Info("Nacos config center enabled, loading config from Nacos...")
		nacosSource, err := config.NewNacosConfigSource(bootstrap.Registry)
		if err != nil {
			log.Errorf("failed to create nacos config source: %v", err)
			panic(err)
		}
		
		// 创建多源配置 (Nacos 优先级高于本地文件)
		finalConfig = kratosConfig.New(
			kratosConfig.WithSource(
				file.NewSource(flagconf),  // 本地配置作为默认值
				nacosSource,                // Nacos 配置覆盖本地配置
			),
		)
	} else {
		// 仅使用本地文件配置
		log.Info("Using local file config only")
		finalConfig = localConfig
	}
	
	defer finalConfig.Close()
	
	// 第三步: 加载最终配置
	if err := finalConfig.Load(); err != nil {
		panic(err)
	}
	
	var bc conf.Bootstrap
	if err := finalConfig.Scan(&bc); err != nil {
		panic(err)
	}
	
	// 第四步: 启动应用
	app, cleanup, err := wireApp(bc.Server, bc.Data, bc.Registry, logger)
	if err != nil {
		panic(err)
	}
	defer cleanup()

	if err := app.Run(); err != nil {
		panic(err)
	}
}
```

---

## 5. 实施步骤

### 5.1 配置改造
1. ✅ 修改 `internal/conf/conf.proto` 添加 Registry 配置
2. ✅ 运行 `make config` 重新生成 `internal/conf/conf.pb.go`
3. ✅ 更新 `configs/config.yaml` 添加 Nacos 配置

### 5.2 代码实现
1. ✅ 创建 `internal/registry/nacos.go` 实现服务注册
2. ✅ 创建 `internal/registry/registry.go` 提供 ProviderSet
3. ✅ 更新 `cmd/demos/main.go` 添加 Registrar 支持
4. ✅ 更新 `cmd/demos/wire.go` 添加 registry 依赖
5. ✅ 运行 `cd cmd/demos && wire` 重新生成 `wire_gen.go`

### 5.3 可选增强 (配置中心)
1. 创建 `internal/config/nacos.go` 实现配置拉取
2. 修改 `cmd/demos/main.go` 支持从 Nacos 拉取配置

### 5.4 测试验证
1. 启动 Nacos Server (默认: `http://127.0.0.1:8848`)
2. 编译运行项目: `make build && ./bin/demos -conf ./configs`
3. 验证服务注册: 访问 Nacos 控制台查看服务列表
4. 测试服务调用: 使用 gRPC/HTTP 客户端调用服务

---

## 6. 文件变更清单

### 新增文件
- ✅ `internal/registry/nacos.go` - Nacos 注册中心实现 (所有参数从配置读取)
- ✅ `internal/registry/registry.go` - Registry ProviderSet
- ⚠️ `internal/config/nacos.go` - Nacos 配置中心实现 (可选，所有参数从配置读取)

### 修改文件
- ✅ `internal/conf/conf.proto` - 添加 Registry 配置结构
- ✅ `configs/config.yaml` - 添加 Nacos 配置
- ✅ `cmd/demos/main.go` - 添加服务注册支持
- ✅ `cmd/demos/wire.go` - 更新依赖注入

### 重新生成文件
- ⚠️ `internal/conf/conf.pb.go` - 通过 `make config` 生成 (不手动修改)
- ⚠️ `cmd/demos/wire_gen.go` - 通过 `wire` 命令生成 (不手动修改)

**注意**: 所有命名包含 `_gen` 的文件都不会手动修改，只通过工具重新生成。

---

## 7. 配置说明

### 7.1 Nacos 服务器配置参数 (ServerConfig)

| 参数 | 类型 | 说明 | 默认值 | 是否必填 |
|-----|-----|-----|-------|---------|
| `ip_addr` | string | Nacos 服务器 IP 地址 | 127.0.0.1 | ✅ 是 |
| `port` | uint64 | Nacos 服务器 HTTP 端口 | 8848 | ✅ 是 |
| `scheme` | string | 协议类型 (http/https) | http | ❌ 否 |
| `context_path` | string | Nacos 上下文路径 | /nacos | ❌ 否 |
| `grpc_port` | uint64 | Nacos 2.x gRPC 端口 | 9848 | ❌ 否 |

### 7.2 Nacos 客户端配置参数 (ClientConfig)

| 参数 | 类型 | 说明 | 默认值 | 是否必填 |
|-----|-----|-----|-------|---------|
| `namespace_id` | string | 命名空间 ID (用于环境隔离) | public | ✅ 是 |
| `timeout_ms` | uint64 | 请求超时时间(毫秒) | 5000 | ✅ 是 |
| `not_load_cache_at_start` | bool | 启动时不加载本地缓存 | true | ❌ 否 |
| `log_dir` | string | SDK 日志存储目录 | ./tmp/nacos/log | ✅ 是 |
| `cache_dir` | string | SDK 缓存目录 | ./tmp/nacos/cache | ✅ 是 |
| `log_level` | string | 日志级别 (debug/info/warn/error) | info | ❌ 否 |
| `username` | string | 鉴权用户名 (开启鉴权时必填) | - | ❌ 否 |
| `password` | string | 鉴权密码 (开启鉴权时必填) | - | ❌ 否 |
| `endpoint` | string | 地址服务器端点 | - | ❌ 否 |
| `region_id` | string | 地域 ID (阿里云) | - | ❌ 否 |
| `access_key` | string | AccessKey (阿里云 KMS) | - | ❌ 否 |
| `secret_key` | string | SecretKey (阿里云 KMS) | - | ❌ 否 |
| `open_kms` | bool | 是否开启 KMS 加密 | false | ❌ 否 |
| `rotate_time` | string | 日志轮转时间 (如: 1h, 24h) | 1h | ❌ 否 |
| `max_age` | int32 | 日志最大保留天数 | 3 | ❌ 否 |

### 7.3 Nacos 配置中心参数 (ConfigConfig, 可选)

| 参数 | 类型 | 说明 | 默认值 | 是否必填 |
|-----|-----|-----|-------|---------|
| `enable` | bool | 是否启用配置中心 | false | ✅ 是 |
| `group` | string | 配置组 | DEFAULT_GROUP | ✅ 是 |
| `data_id` | string | 配置 DataId (如: demos.yaml) | - | ✅ 是 |

### 7.4 配置示例

#### 基础配置 (最小化)
```yaml
registry:
  nacos:
    server:
      - ip_addr: 127.0.0.1
        port: 8848
    client:
      namespace_id: public
      timeout_ms: 5000
      log_dir: ./tmp/nacos/log
      cache_dir: ./tmp/nacos/cache
```

#### 完整配置 (生产环境)
```yaml
registry:
  nacos:
    # 集群配置
    server:
      - ip_addr: 192.168.1.100
        port: 8848
        scheme: http
        context_path: /nacos
        grpc_port: 9848
      - ip_addr: 192.168.1.101
        port: 8848
        scheme: http
        context_path: /nacos
        grpc_port: 9848
    
    # 客户端配置
    client:
      namespace_id: prod              # 生产环境命名空间
      timeout_ms: 10000               # 10秒超时
      not_load_cache_at_start: false  # 启动时加载缓存
      log_dir: /var/log/nacos
      cache_dir: /var/cache/nacos
      log_level: warn
      username: nacos_user            # 鉴权用户名
      password: nacos_pass            # 鉴权密码
      rotate_time: 24h
      max_age: 7
    
    # 配置中心 (可选)
    config:
      enable: true
      group: PROD_GROUP
      data_id: demos-prod.yaml
```

#### 阿里云 MSE Nacos 配置
```yaml
registry:
  nacos:
    server:
      - ip_addr: mse-xxxxx-nacos-ans.mse.aliyuncs.com
        port: 8848
        scheme: http
    client:
      namespace_id: your-namespace-id
      timeout_ms: 5000
      log_dir: ./tmp/nacos/log
      cache_dir: ./tmp/nacos/cache
      access_key: your-access-key     # 阿里云 AccessKey
      secret_key: your-secret-key     # 阿里云 SecretKey
      endpoint: mse-xxxxx-nacos-ans.mse.aliyuncs.com
      region_id: cn-hangzhou
```

### 7.5 环境变量支持 (可选)

虽然所有配置都从配置文件读取，但也可以通过环境变量覆盖:

```bash
# 示例: 通过环境变量指定不同的配置文件
export NACOS_NAMESPACE=dev
export NACOS_SERVER=192.168.1.100:8848

# 在代码中可以读取环境变量并覆盖配置
# 但推荐直接修改配置文件或使用 Nacos 配置中心
```

---

## 8. 优势与收益

### 8.1 服务注册发现
- ✅ 服务自动注册到 Nacos
- ✅ 客户端自动发现服务实例
- ✅ 支持健康检查和自动摘除
- ✅ 支持多实例负载均衡
- ✅ 所有配置从配置文件读取，灵活可控

### 8.2 配置中心
- ✅ 配置统一管理
- ✅ 配置动态更新 (无需重启)
- ✅ 配置版本管理
- ✅ 多环境配置隔离 (通过 namespace)
- ✅ 配置热更新监听
- ✅ 所有参数可配置化，无硬编码

### 8.3 生产级特性
- ✅ 使用 Kratos 官方包，稳定可靠
- ✅ 完整的依赖注入支持 (Wire)
- ✅ 代码结构清晰，易于维护
- ✅ 兼容现有 Kratos 生态
- ✅ 支持集群部署 (多个 Nacos Server)
- ✅ 支持鉴权和安全加密
- ✅ 支持阿里云 MSE Nacos

### 8.4 配置管理优势
- ✅ **零硬编码**: 所有 Nacos 相关配置都从配置文件读取
- ✅ **灵活切换**: 可以通过配置文件轻松切换环境 (dev/test/prod)
- ✅ **配置继承**: 本地文件配置作为默认值，Nacos 配置覆盖
- ✅ **向下兼容**: 未启用 Nacos 时，不影响现有功能
- ✅ **配置验证**: 通过 protobuf 定义，类型安全

---

## 9. 注意事项

1. **不修改 `_gen` 文件**: `conf.pb.go` 和 `wire_gen.go` 只通过工具生成
2. **Nacos 版本兼容**: 确保 Nacos Server 版本 >= 1.x (推荐 2.x)
3. **依赖已安装**: 项目已包含所需依赖，无需额外安装
4. **配置灵活性**: 
   - 所有配置参数从 `configs/config.yaml` 读取
   - 支持本地配置和 Nacos 配置两种模式
   - 可通过 `config.enable` 开关控制是否使用配置中心
5. **渐进式迁移**: 
   - 服务注册和配置中心可以分步实施
   - 未配置 Registry 时，服务不会注册，不影响现有功能
6. **集群支持**: 
   - 支持配置多个 Nacos Server (高可用)
   - 客户端会自动故障转移
7. **安全性**: 
   - 生产环境建议启用鉴权 (username/password)
   - 敏感信息建议使用环境变量或密钥管理服务
8. **日志与缓存**: 
   - 确保 `log_dir` 和 `cache_dir` 目录可写
   - Windows 系统建议使用绝对路径 (如: `C:/nacos/log`)
9. **配置热更新**: 
   - 使用 Nacos 配置中心时，配置会自动监听变化
   - 业务代码需要实现配置变更回调
10. **测试建议**: 
    - 先在本地单机 Nacos 测试
    - 确认服务注册成功后再配置生产环境

---

## 10. 参考资料

- [Kratos 官方文档](https://go-kratos.dev/)
- [Kratos Nacos Registry](https://github.com/go-kratos/kratos/tree/main/contrib/registry/nacos)
- [Kratos Nacos Config](https://github.com/go-kratos/kratos/tree/main/contrib/config/nacos)
- [Nacos 官方文档](https://nacos.io/)
- 项目参考: `template/server.go`, `template/client.go`

---

## 11. 快速启动指南

### 11.1 启动 Nacos Server

```bash
# 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.2.3/nacos-server-2.2.3.zip
unzip nacos-server-2.2.3.zip
cd nacos/bin

# Linux/Mac 启动 (单机模式)
sh startup.sh -m standalone

# Windows 启动 (单机模式)
startup.cmd -m standalone

# 访问控制台: http://127.0.0.1:8848/nacos
# 默认用户名密码: nacos/nacos
```

### 11.2 修改配置文件

编辑 `configs/config.yaml`:

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 1s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 1s

data:
  database:
    driver: mysql
    source: root:root@tcp(127.0.0.1:3306)/test?parseTime=True&loc=Local
  redis:
    addr: 127.0.0.1:6379
    read_timeout: 0.2s
    write_timeout: 0.2s

# Nacos 服务注册与配置中心
registry:
  nacos:
    server:
      - ip_addr: 127.0.0.1
        port: 8848
        scheme: http
    client:
      namespace_id: public
      timeout_ms: 5000
      not_load_cache_at_start: true
      log_dir: ./tmp/nacos/log
      cache_dir: ./tmp/nacos/cache
      log_level: info
    # 可选: 配置中心
    config:
      enable: false
      group: DEFAULT_GROUP
      data_id: demos.yaml
```

### 11.3 实施改造

```bash
# 1. 更新 proto 定义
make config

# 2. 创建 registry 模块
mkdir -p internal/registry
# 创建 nacos.go 和 registry.go (参考提案第 3.1、3.2 节)

# 3. 更新 Wire 配置
cd cmd/demos
wire

# 4. 编译运行
cd ../..
make build
./bin/demos -conf ./configs
```

### 11.4 验证服务注册

```bash
# 查看服务列表
curl http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10

# 查看服务详情
curl http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=demos

# 或访问 Nacos 控制台
# http://127.0.0.1:8848/nacos 
# 服务管理 -> 服务列表 -> 查看 "demos" 服务
```

### 11.5 测试服务调用

```bash
# HTTP 调用
curl http://localhost:8000/helloworld/kratos

# gRPC 调用 (需要安装 grpcurl)
grpcurl -plaintext -d '{"name":"kratos"}' localhost:9000 helloworld.v1.Greeter/SayHello
```

---

## 12. 完整配置文件示例

### 12.1 开发环境 (`configs/config-dev.yaml`)

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 1s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 1s

data:
  database:
    driver: mysql
    source: root:root@tcp(127.0.0.1:3306)/demos_dev?parseTime=True
  redis:
    addr: 127.0.0.1:6379
    read_timeout: 0.2s
    write_timeout: 0.2s

registry:
  nacos:
    server:
      - ip_addr: 127.0.0.1
        port: 8848
    client:
      namespace_id: dev
      timeout_ms: 5000
      log_dir: ./tmp/nacos/log
      cache_dir: ./tmp/nacos/cache
      log_level: debug
    config:
      enable: false
```

### 12.2 测试环境 (`configs/config-test.yaml`)

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 1s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 1s

data:
  database:
    driver: mysql
    source: root:root@tcp(192.168.1.100:3306)/demos_test?parseTime=True
  redis:
    addr: 192.168.1.101:6379
    read_timeout: 0.2s
    write_timeout: 0.2s

registry:
  nacos:
    server:
      - ip_addr: 192.168.1.200
        port: 8848
    client:
      namespace_id: test
      timeout_ms: 5000
      log_dir: /var/log/nacos
      cache_dir: /var/cache/nacos
      log_level: info
      username: nacos
      password: nacos
    config:
      enable: true
      group: TEST_GROUP
      data_id: demos.yaml
```

### 12.3 生产环境 (`configs/config-prod.yaml`)

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 3s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 3s

data:
  database:
    driver: mysql
    source: prod_user:prod_pass@tcp(db.prod.com:3306)/demos?parseTime=True
  redis:
    addr: redis.prod.com:6379
    read_timeout: 0.5s
    write_timeout: 0.5s

registry:
  nacos:
    server:
      - ip_addr: nacos1.prod.com
        port: 8848
        scheme: https
      - ip_addr: nacos2.prod.com
        port: 8848
        scheme: https
      - ip_addr: nacos3.prod.com
        port: 8848
        scheme: https
    client:
      namespace_id: prod
      timeout_ms: 10000
      not_load_cache_at_start: false
      log_dir: /var/log/demos/nacos
      cache_dir: /var/cache/demos/nacos
      log_level: warn
      username: prod_nacos_user
      password: ${NACOS_PASSWORD}  # 从环境变量读取
      rotate_time: 24h
      max_age: 30
    config:
      enable: true
      group: PROD_GROUP
      data_id: demos-prod.yaml
```

### 12.4 阿里云 MSE Nacos (`configs/config-aliyun.yaml`)

```yaml
server:
  http:
    addr: 0.0.0.0:8000
    timeout: 1s
  grpc:
    addr: 0.0.0.0:9000
    timeout: 1s

data:
  database:
    driver: mysql
    source: user:pass@tcp(rm-xxxxx.mysql.rds.aliyuncs.com:3306)/demos
  redis:
    addr: r-xxxxx.redis.rds.aliyuncs.com:6379
    read_timeout: 0.2s
    write_timeout: 0.2s

registry:
  nacos:
    server:
      - ip_addr: mse-xxxxx-nacos-ans.mse.aliyuncs.com
        port: 8848
        scheme: http
    client:
      namespace_id: your-namespace-id
      timeout_ms: 5000
      log_dir: /var/log/nacos
      cache_dir: /var/cache/nacos
      log_level: info
      endpoint: mse-xxxxx-nacos-ans.mse.aliyuncs.com
      region_id: cn-hangzhou
      access_key: ${ALIYUN_ACCESS_KEY}
      secret_key: ${ALIYUN_SECRET_KEY}
    config:
      enable: true
      group: DEFAULT_GROUP
      data_id: demos.yaml
```

---

## 13. 常见问题 FAQ

### Q1: 如何在不同环境使用不同配置?
**A**: 通过 `-conf` 参数指定不同的配置文件:
```bash
# 开发环境
./bin/demos -conf ./configs/config-dev.yaml

# 生产环境
./bin/demos -conf ./configs/config-prod.yaml
```

### Q2: 服务无法注册到 Nacos?
**A**: 检查以下项:
1. Nacos Server 是否正常启动 (`curl http://127.0.0.1:8848/nacos`)
2. 配置文件中 `ip_addr` 和 `port` 是否正确
3. 查看日志: `./tmp/nacos/log/naming.log`
4. 检查网络连接和防火墙

### Q3: 如何禁用 Nacos 服务注册?
**A**: 两种方式:
1. 不配置 `registry` 字段 (注释掉整个 registry 配置)
2. 在 `newApp` 中判断 `registrar` 是否为 nil

### Q4: 配置中心和服务注册可以分开使用吗?
**A**: 可以，完全独立:
- 只使用服务注册: 配置 `registry.nacos`，但不配置 `config` 或设置 `config.enable=false`
- 只使用配置中心: 实现 `config.NewNacosConfigSource` 但不注入 `registry.Registrar`

### Q5: 如何动态刷新配置?
**A**: Kratos 的 `config.Config` 支持 `Watch()` 监听配置变化:
```go
// 监听配置变化
go func() {
    if err := c.Watch("server.http.addr", func(key string, value config.Value) {
        log.Infof("config changed, key: %s, value: %v", key, value)
        // 处理配置变更逻辑
    }); err != nil {
        log.Error(err)
    }
}()
```

### Q6: 生产环境如何管理敏感配置 (密码、密钥)?
**A**: 推荐方式:
1. 使用环境变量: `password: ${NACOS_PASSWORD}`
2. 使用 Nacos KMS 加密: 设置 `open_kms: true`
3. 使用 Kubernetes Secret 挂载
4. 使用专业的密钥管理服务 (如 Vault)

### Q7: 如何支持多 Nacos 集群?
**A**: 在 `server` 数组中配置多个节点:
```yaml
server:
  - ip_addr: nacos1.example.com
    port: 8848
  - ip_addr: nacos2.example.com
    port: 8848
  - ip_addr: nacos3.example.com
    port: 8848
```
客户端会自动做负载均衡和故障转移。

### Q8: Windows 系统路径配置注意事项?
**A**: 
- 使用正斜杠: `./tmp/nacos/log` 或 `C:/nacos/log`
- 避免使用反斜杠: ~~`C:\nacos\log`~~
- 确保目录有写权限

---

## 14. 总结

本提案提供了一套完整的 Kratos 项目集成 Nacos 方案，主要特点:

✅ **零硬编码**: 所有配置从配置文件读取  
✅ **类型安全**: 通过 Protobuf 定义配置结构  
✅ **灵活可控**: 支持本地配置和 Nacos 配置双模式  
✅ **生产可用**: 支持集群、鉴权、加密等企业级特性  
✅ **易于维护**: 代码结构清晰，符合 Kratos 最佳实践  
✅ **向下兼容**: 不影响现有功能，渐进式迁移  

通过本提案，可以快速将 Kratos 项目改造为支持 Nacos 服务注册发现和配置中心的微服务架构。
