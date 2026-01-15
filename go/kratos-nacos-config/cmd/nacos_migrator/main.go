package main

import (
	"fmt"
	"log"

	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

// ----------- 远程 NACOS 配置 -----------
var REMOTE_NACOS = "192.168.1.xxx"
var REMOTE_PORT uint64 = 0
var REMOTE_NAMESPACE = "xxx"
var REMOTE_USERNAME = "xxx"
var REMOTE_PASSWORD = "xxxx"

// ----------- 本地 NACOS 配置 -----------
var LOCAL_NACOS = "127.0.0.1"
var LOCAL_PORT uint64 = 8848

// wanghongwei-31550
var LOCAL_NAMESPACE = "xxx"

// ----------- 固定配置 -----------
var GROUP = "DEFAULT_GROUP"
var DATA_ID_LIST = []string{
	"xxx",
}

func createConfigClient(serverIP string, port uint64, namespace, username, password string) config_client.IConfigClient {
	sc := []constant.ServerConfig{
		{
			IpAddr: serverIP,
			Port:   port,
		},
	}

	cc := constant.ClientConfig{
		NamespaceId:         namespace,
		TimeoutMs:           5000,
		NotLoadCacheAtStart: true,
		LogDir:              "./nacos/log",
		CacheDir:            "./nacos/cache",
		LogLevel:            "warn",
	}
	if username != "" {
		cc.Username = username
	}
	if password != "" {
		cc.Password = password
	}

	client, err := clients.CreateConfigClient(map[string]interface{}{
		"serverConfigs": sc,
		"clientConfig":  cc,
	})
	if err != nil {
		log.Fatalf("创建 Nacos 配置客户端失败: %v", err)
	}
	return client
}

func main() {
	fmt.Println("🔐 连接远程 Nacos...")
	remoteClient := createConfigClient(REMOTE_NACOS, REMOTE_PORT, REMOTE_NAMESPACE, REMOTE_USERNAME, REMOTE_PASSWORD)

	fmt.Println("🛠️ 连接本地 Nacos...")
	localClient := createConfigClient(LOCAL_NACOS, LOCAL_PORT, LOCAL_NAMESPACE, "", "")

	fmt.Printf("🚚 准备迁移 %d 个配置...\n", len(DATA_ID_LIST))
	for _, dataId := range DATA_ID_LIST {
		fmt.Printf("📥 正在拉取: %s\n", dataId)
		content, err := remoteClient.GetConfig(vo.ConfigParam{
			DataId: dataId,
			Group:  GROUP,
		})
		if err != nil {
			log.Fatalf("❌ 获取配置失败: %s, err: %v", dataId, err)
		}

		fmt.Printf("📤 正在发布到本地 Nacos: %s\n", dataId)
		success, err := localClient.PublishConfig(vo.ConfigParam{
			DataId:  dataId,
			Group:   GROUP,
			Content: content,
			Type:    "yaml", // 必须设置类型
		})
		if err != nil || !success {
			log.Fatalf("❌ 发布配置失败: %s, err: %v", dataId, err)
		}

		fmt.Printf("✅ 成功迁移: %s\n", dataId)
	}

	fmt.Println("🎉 所有配置迁移完成！")
}
