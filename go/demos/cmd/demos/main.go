package main

import (
	nacosConfig "demos/template"
	"flag"
	"fmt"
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
	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"

	_ "go.uber.org/automaxprocs"
)

// go build -ldflags "-X main.Version=x.y.z"
var (
	// Name is the name of the compiled software.
	Name string = "demos"
	// Version is the version of the compiled software.
	Version string = "v1.0.0"
	// flagconf is the config flag.
	flagconf string

	id, _ = os.Hostname()
)

func init() {
	flag.StringVar(&flagconf, "conf", "configs", "config path, eg: -conf config.yaml")
}

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

	// 创建配置源列表
	var configSources []config.Source

	// 首先加载本地配置文件作为基础配置
	configSources = append(configSources, file.NewSource(flagconf))

	// 加载配置
	c := config.New(config.WithSource(configSources...))
	defer c.Close()

	if err := c.Load(); err != nil {
		panic(err)
	}

	var bc conf.Bootstrap
	if err := c.Scan(&bc); err != nil {
		panic(err)
	}

	// 如果启用了 Nacos 配置中心，添加 Nacos 配置源
	if bc.Registry != nil && bc.Registry.Nacos != nil && bc.Registry.Nacos.Config != nil && bc.Registry.Nacos.Config.Enable {
		nacosConfigConfig := bc.Registry.Nacos.Config

		// 构建 ServerConfig
		serverConfigs := make([]constant.ServerConfig, 0, len(bc.Registry.Nacos.Server))
		for _, s := range bc.Registry.Nacos.Server {
			sc := constant.ServerConfig{
				IpAddr:      s.IpAddr,
				Port:        s.Port,
				Scheme:      s.Scheme,
				ContextPath: s.ContextPath,
			}
			serverConfigs = append(serverConfigs, sc)
		}

		// 构建 ClientConfig
		cc := bc.Registry.Nacos.Client
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
		}

		// 创建 Nacos ConfigClient
		configClient, err := clients.NewConfigClient(
			vo.NacosClientParam{
				ClientConfig:  &clientConfig,
				ServerConfigs: serverConfigs,
			},
		)
		if err != nil {
			panic(err)
		}

		// 为每个 data_id 创建配置源
		for _, dataID := range nacosConfigConfig.DataId {
			nacosSource := nacosConfig.NewConfigSource(configClient,
				nacosConfig.WithGroup(nacosConfigConfig.Group),
				nacosConfig.WithDataID(dataID),
			)
			configSources = append(configSources, nacosSource)

			fmt.Println(dataID)
			fmt.Println(configClient)
		}

		// 重新加载配置，包含 Nacos 配置源
		c = config.New(config.WithSource(configSources...))
		defer c.Close()

		if err := c.Load(); err != nil {
			panic(err)
		}

		if err := c.Scan(&bc); err != nil {
			panic(err)
		}
	}

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
