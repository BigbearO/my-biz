package main

import (
	"context"
	"logViewer/hertz/api"
	"logViewer/infra/es"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/protocol/consts"
	"github.com/spf13/viper"
)

func main() {

	InitConfig()
	port := viper.GetString("server.port")
	if port == "" {
		port = "8008"
	}
	//完善一下，能指定port
	h := server.Default(server.WithHostPorts(":" + port))

	// 初始化 ES 客户端
	client := es.NewESClient()

	// 依赖注入 ES 客户端
	api.SetESClient(client)

	// 路由设置
	h.GET("/ping", func(ctx context.Context, c *app.RequestContext) {
		c.JSON(consts.StatusOK, map[string]string{"message": "pong"})
	})

	// 健康检查
	h.GET("/health", api.TestHealth)

	// 日志查询
	h.POST("/search", api.SearchLogs)

	// 演示数据插入
	h.POST("/demo", api.InsertDemo)

	h.Spin()

}

func InitConfig() {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("etc")
	if err := viper.ReadInConfig(); err != nil {
		panic("Failed to read config: " + err.Error())
	}
}
