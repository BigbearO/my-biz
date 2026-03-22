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
