package main

import (
	"logViewer/api"
	"logViewer/infra/es"
	"mime"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/spf13/viper"
)

//func main() {
//	engine := startGin()
//	port := viper.GetInt("server.port")
//	engine.Run(":" + strconv.Itoa(port))
//}

func init() {
	_ = mime.AddExtensionType(".js", "application/javascript")
}

func startGin() *gin.Engine {
	InitConfig()
	// 上面的运行了，到时候直接 viper.GetString( 就行
	InitEs()

	router := gin.Default()
	//  可以自定义
	router.Use(cors.Default())

	// 静态资源加载，本例为css,js以及资源图片
	//StaticFS 是加载一个完整的目录资源
	//router.StaticFS("/public", http.Dir("/Users/fu/GolandProjects/GinDemo/web/static"))
	////StaticFile 是加载单个文件
	//router.StaticFile("/logo.jpg", "./web/static/kaiyuan10nian.jpg")
	//// 导入所有模板，多级目录结构需要这样写
	//router.LoadHTMLGlob("web/templete/*")
	//router.StaticFile("/static/index.html", "./static/index.html")
	//router.StaticFile("/static/in.html", "./static/in.html")
	//router.StaticFile("/static/favicon.ico", "./static/favicon.ico")
	//router.Static("/assets", "./static/assets")

	router.Static("/static", "./static")

	log := router.Group("/log")
	{
		log.GET("/view", api.Search)
		log.GET("/add", api.Insert)
		log.GET("/test", api.Test)
	}

	return router
}

func InitConfig() {
	workDir, _ := os.Getwd()
	viper.SetConfigName("config")
	viper.SetConfigType("yml")
	viper.AddConfigPath(workDir + "/etc")
	err := viper.ReadInConfig()
	if err != nil {
		panic("")
	}
}

// todo 优化注入
func InitEs() {
	esClient := es.NewESClient()

	api.EsCLinet = esClient
}
