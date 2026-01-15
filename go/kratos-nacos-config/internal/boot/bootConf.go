package boot

import (
	"flag"
	"fmt"
	"kratosNacosConfig/internal/conf"
	"kratosNacosConfig/pkg"

	knacos "github.com/go-kratos/kratos/contrib/config/nacos/v2"
	"github.com/go-kratos/kratos/v2/config"
	"github.com/go-kratos/kratos/v2/config/file"
	"github.com/gogf/gf/util/gconv"
	"github.com/google/uuid"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
)

var (
	flagConf string
)

func init() {
	// 获取命令行参数
	flag.StringVar(&flagConf, "conf", "configs", "config path, eg: -conf config.yaml")
	flag.Parse()
}

type BootConf struct{}

// Notice nacosBase 这个命名空间初始化的 client
var nacosBaseClient config_client.IConfigClient

// Notice biz层需要用到 nacosBase 这个命名空间的client 调用这个方法获取即可
func GetNacosBaseClient() config_client.IConfigClient {
	return nacosBaseClient
}

// 本地的yaml配置 主要配置连接 nacos 基础配置的配置项
func (c *BootConf) RunLocalConfig() *conf.Bootstrap {

	// 获取指定路径 yaml文件 的配置
	configParams := config.New(
		config.WithSource(
			file.NewSource(flagConf),
		),
	)

	if errLoad := configParams.Load(); errLoad != nil {
		panic(errLoad)
	}

	var bc conf.Bootstrap
	if errScan := configParams.Scan(&bc); errScan != nil {
		panic(errScan)
	}

	fmt.Println("本地yaml的配置:>>>>>> ", gconv.String(&bc))

	// Notice 初始化 nacosBaseClient
	nacosBaseClient = pkg.NewNacos(
		bc.GetNacos().GetAddr(),
		bc.GetNacos().GetBaseYaml().GetNamespaceId(),
		bc.GetNacos().GetPort(),
	)

	return &bc

}

// 根据nacos的基础配置，从nacos中的获取 服务/数据库/redis等的配置
// Notice 如果项目部署到 k8s上，可以使用 os.Getenv 方法从环境变量中获取 addr等信息，不用再加一个本地的config.yaml了～
// 其实效果都一样～
func (c *BootConf) RunNacosConfig(group, dataId string) *conf.Bootstrap {
	var source config.Source

	// 连接配置好的 nacosClient 根据配置项获取对应的基础配置
	// Notice 当然可以直接把配置写到本地的config.yaml文件中，这里就是演示一下效果～
	source = c.nacosRun(group, dataId)
	configParams := config.New(config.WithSource(source))
	if errLoad := configParams.Load(); errLoad != nil {
		panic(errLoad)
	}

	var bc conf.Bootstrap
	if errScan := configParams.Scan(&bc); errScan != nil {
		panic(errScan)
	}

	serverId, _ := uuid.NewUUID()
	bc.Server.Id = serverId.String()

	fmt.Println("远程nacos中的yaml文件的配置:>>>>>> ", gconv.String(&bc))

	return &bc
}

func (c *BootConf) nacosRun(group, dataId string) config.Source {

	return knacos.NewConfigSource(
		// Notice 把之前初始化过的 nacosBaseClient 传进去
		nacosBaseClient,
		knacos.WithGroup(group),
		knacos.WithDataID(dataId),
	)
}
