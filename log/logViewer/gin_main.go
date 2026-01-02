package main

import (
	"logViewer/gin/api"
	"logViewer/infra/es"
	"mime"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spf13/viper"
)

func init() {
	_ = mime.AddExtensionType(".js", "application/javascript")
}

func main2() {
	InitConfig()
	InitEs()

	router := gin.Default()
	router.Use(cors.Default())

	// 静态资源服务（使用相对路径指向项目根目录的 static）
	router.Static("/static", "../static")

	log := router.Group("/log")
	{
		log.GET("/view", api.SearchLogs)
		log.GET("/add", api.InsertDemo)
		log.GET("/test", api.TestHealth)
	}

	port := viper.GetString("server.port")
	if port == "" {
		port = "8008"
	}

	if err := router.Run(":" + port); err != nil {
		panic("Failed to start Gin server: " + err.Error())
	}
}

func InitConfig() {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("etc")
	if err := viper.ReadInConfig(); err != nil {
		panic("Failed to read config: " + err.Error())
	}
}

func InitEs() {
	esClient := es.NewESClient()
	api.SetESClient(esClient)
}
