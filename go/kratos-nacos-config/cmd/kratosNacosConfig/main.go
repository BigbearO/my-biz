package main

import (
	"kratosNacosConfig/internal/boot"
	"kratosNacosConfig/internal/conf"

	"github.com/go-kratos/kratos/v2"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-kratos/kratos/v2/transport/grpc"
	"github.com/go-kratos/kratos/v2/transport/http"

	_ "go.uber.org/automaxprocs"
)

func newApp(conf *conf.Server, logger log.Logger, gs *grpc.Server, hs *http.Server) *kratos.App {
	return kratos.New(
		kratos.ID(conf.GetId()),
		kratos.Name(conf.GetName()),
		kratos.Version(conf.GetVersion()),
		kratos.Metadata(map[string]string{}),
		kratos.Logger(logger),
		kratos.Server(
			gs,
			hs,
		),
	)
}

func main() {

	// Notice 本地yaml配置 只有连接nacos的参数
	localConfig := boot.NewBootConf().RunLocalConfig()
	// Notice 从nacos中获取配置 依赖本地配置项 ～ 当然这些参数也可以放在环境变量、当成项目运行的参数，都可以
	nacosYamlConf := boot.NewBootConf().RunNacosConfig(
		localConfig.GetNacos().GetBaseYaml().GetGroup(),
		localConfig.GetNacos().GetBaseYaml().GetDataId(),
	)
	// Notice 初始化log
	logger := boot.NewBootLog(nacosYamlConf).Run()

	app, cleanup, err := wireApp(nacosYamlConf.Server, nacosYamlConf.Data, logger, localConfig, nacosYamlConf)
	if err != nil {
		panic(err)
	}
	defer cleanup()

	// start and wait for stop signal
	if errRun := app.Run(); errRun != nil {
		panic(errRun)
	}
}
