package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

// Config 存储转发服务的配置信息
type Config struct {
	ListenAddress string `json:"listen_address"` // 监听地址
	RemoteAddress string `json:"remote_address"` // 远程目标地址
	Protocol      string `json:"protocol"`       // 协议类型：tcp 或 udp
	BufferSize    int    `json:"buffer_size"`    // 缓冲区大小
	LogLevel      string `json:"log_level"`      // 日志级别
}

// 全局配置变量和读写锁
var (
	config      *Config
	configMutex sync.RWMutex
)

// 初始化日志
func initLogger() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)
}

// 加载配置文件
func loadConfig(filePath string) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("无法打开配置文件: %v", err)
	}
	defer file.Close()

	newConfig := &Config{}
	decoder := json.NewDecoder(file)
	if err := decoder.Decode(newConfig); err != nil {
		return fmt.Errorf("无法解析配置文件: %v", err)
	}

	// 设置默认值
	if newConfig.BufferSize <= 0 {
		newConfig.BufferSize = 4096
	}
	if newConfig.LogLevel == "" {
		newConfig.LogLevel = "info"
	}
	newConfig.Protocol = strings.ToLower(newConfig.Protocol)

	// 更新全局配置，使用读写锁保护
	configMutex.Lock()
	config = newConfig
	configMutex.Unlock()

	log.Printf("配置已更新: %+v", newConfig)
	return nil
}

// 转发TCP连接
func handleTCPConnection(conn net.Conn) {
	defer conn.Close()

	// 获取最新配置
	configMutex.RLock()
	remoteAddr := config.RemoteAddress
	configMutex.RUnlock()

	// 连接到远程服务器
	remoteConn, err := net.Dial("tcp", remoteAddr)
	if err != nil {
		log.Printf("无法连接到远程服务器 %s: %v", remoteAddr, err)
		return
	}
	defer remoteConn.Close()

	log.Printf("TCP连接已建立: %s -> %s", conn.RemoteAddr(), remoteAddr)

	// 创建双向数据转发
	go func() {
		_, err := io.Copy(remoteConn, conn)
		if err != nil {
			log.Printf("转发数据到远程服务器时出错: %v", err)
		}
	}()

	_, err = io.Copy(conn, remoteConn)
	if err != nil {
		log.Printf("从远程服务器接收数据时出错: %v", err)
	}

	log.Printf("TCP连接已关闭: %s -> %s", conn.RemoteAddr(), remoteAddr)
}

// 启动TCP转发服务
func startTCPForwarder() error {
	// 只在启动时读取一次监听地址，后续不更新
	configMutex.RLock()
	listenAddr := config.ListenAddress
	configMutex.RUnlock()

	listener, err := net.Listen("tcp", listenAddr)
	if err != nil {
		return fmt.Errorf("无法监听TCP地址 %s: %v", listenAddr, err)
	}
	defer listener.Close()

	log.Printf("TCP转发服务已启动，监听地址: %s", listenAddr)

	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Printf("接受TCP连接时出错: %v", err)
			continue
		}

		// 处理连接
		go handleTCPConnection(conn)
	}
}

// 启动UDP转发服务
func startUDPForwarder() error {
	// 监听本地UDP地址
	configMutex.RLock()
	listenAddr := config.ListenAddress
	configMutex.RUnlock()

	localAddr, err := net.ResolveUDPAddr("udp", listenAddr)
	if err != nil {
		return fmt.Errorf("无法解析本地UDP地址 %s: %v", listenAddr, err)
	}

	// 创建UDP连接
	conn, err := net.ListenUDP("udp", localAddr)
	if err != nil {
		return fmt.Errorf("无法监听UDP地址 %s: %v", listenAddr, err)
	}
	defer conn.Close()

	log.Printf("UDP转发服务已启动，监听地址: %s", listenAddr)

	// 缓冲区
	configMutex.RLock()
	bufferSize := config.BufferSize
	configMutex.RUnlock()
	buffer := make([]byte, bufferSize)

	for {
		// 读取数据
		n, clientAddr, err := conn.ReadFromUDP(buffer)
		if err != nil {
			log.Printf("读取UDP数据时出错: %v", err)
			continue
		}

		// 获取最新远程地址
		configMutex.RLock()
		remoteAddr := config.RemoteAddress
		configMutex.RUnlock()

		remoteUDPAddr, err := net.ResolveUDPAddr("udp", remoteAddr)
		if err != nil {
			log.Printf("无法解析远程UDP地址 %s: %v", remoteAddr, err)
			continue
		}

		// 发送数据到远程服务器
		_, err = conn.WriteToUDP(buffer[:n], remoteUDPAddr)
		if err != nil {
			log.Printf("发送UDP数据到远程服务器时出错: %v", err)
			continue
		}

		log.Printf("UDP数据转发: %s -> %s, 大小: %d bytes", clientAddr, remoteAddr, n)
	}
}

// 启动配置文件监听
func startConfigWatcher(configFile string) error {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return fmt.Errorf("无法创建配置文件监听器: %v", err)
	}
	defer watcher.Close()

	// 监听配置文件
	if err := watcher.Add(configFile); err != nil {
		return fmt.Errorf("无法监听配置文件 %s: %v", configFile, err)
	}

	log.Printf("已启动配置文件监听: %s", configFile)

	// 处理配置文件变化
	go func() {
		for {
			select {
			case event, ok := <-watcher.Events:
				if !ok {
					return
				}
				// 只处理写事件和创建事件
				if event.Op&(fsnotify.Write|fsnotify.Create) != 0 {
					log.Printf("配置文件 %s 已更改，正在重新加载...", event.Name)
					// 延迟加载，避免文件未完全写入
					time.Sleep(100 * time.Millisecond)
					if err := loadConfig(event.Name); err != nil {
						log.Printf("重新加载配置文件失败: %v", err)
					}
				}
			case err, ok := <-watcher.Errors:
				if !ok {
					return
				}
				log.Printf("配置文件监听错误: %v", err)
			}
		}
	}()

	// 阻塞，保持监听器运行
	select {}
}

func main() {
	// 初始化日志
	initLogger()

	// 加载配置文件
	configFile := "config.json"
	if err := loadConfig(configFile); err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	log.Printf("初始配置已加载: %+v", config)

	// 启动配置文件监听
	go func() {
		if err := startConfigWatcher(configFile); err != nil {
			log.Fatalf("启动配置文件监听失败: %v", err)
		}
	}()

	// 记录初始协议
	configMutex.RLock()
	protocol := config.Protocol
	configMutex.RUnlock()

	// 根据协议启动转发服务
	var err error
	if protocol == "tcp" {
		err = startTCPForwarder()
	} else if protocol == "udp" {
		err = startUDPForwarder()
	} else {
		err = fmt.Errorf("不支持的协议: %s", protocol)
	}

	if err != nil {
		log.Fatalf("启动转发服务失败: %v", err)
	}
}
