package main

import (
	"fmt"
	"log"
	"net/http"
)

func main() {
	// 加载配置
	config, err := LoadConfig("config.json")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 创建代理处理器
	proxyHandler := NewProxyHandler(config.Routes)

	// 创建HTTP服务器
	mux := http.NewServeMux()
	mux.Handle("/", proxyHandler)

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", config.Port),
		Handler: mux,
	}

	fmt.Printf("HTTP Proxy Server starting on port %d\n", config.Port)
	fmt.Println("Configured routes:")
	for _, route := range config.Routes {
		fmt.Printf("  %s -> %s\n", route.PathPrefix, route.TargetURL)
	}
	fmt.Println("\nPress Ctrl+C to stop the server")

	// 启动服务器
	if err := server.ListenAndServe(); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
