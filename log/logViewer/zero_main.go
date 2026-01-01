package main

import (
	"flag"
	"fmt"
	"logViewer/internal/config"
	"logViewer/internal/handler"
	"logViewer/internal/svc"
	"net/http"

	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/rest"
)

var configFile = flag.String("f", "etc/logviewer.yaml", "the config file")

func main() {
	flag.Parse()

	var c config.Config
	conf.MustLoad(*configFile, &c)

	// 创建 HTTP 服务器
	server := rest.MustNewServer(c.RestConf, rest.WithCors("*"), rest.WithFileServer("/static", http.Dir("./static")))
	defer server.Stop()

	// 初始化服务上下文
	svcCtx := svc.NewServiceContext(&c)

	// 注册路由
	handler.RegisterHandlers(server, svcCtx)

	// 打印启动信息
	fmt.Printf("Starting server at %s:%d...\n", c.Host, c.Port)

	// 启动服务器
	server.Start()
}
