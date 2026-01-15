package pkg

import (
	"fmt"

	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"
)

func NewNacos(nacosAddr, nacosNameSpaceId string, port uint64) config_client.IConfigClient {
	fmt.Println("nacosAddr: ", nacosAddr, "port: ", port)

	sc := []constant.ServerConfig{
		*constant.NewServerConfig(nacosAddr, port),
	}

	cc := &constant.ClientConfig{
		NamespaceId:         nacosNameSpaceId, //namespace id
		TimeoutMs:           5000,
		NotLoadCacheAtStart: true,
		Username:            "nacos",
		Password:            "nacos",
		CacheDir:            "./nacos/cache",
		LogLevel:            "error",
	}

	// a more graceful way to create naming client
	client, err := clients.NewConfigClient(
		vo.NacosClientParam{
			ClientConfig:  cc,
			ServerConfigs: sc,
		},
	)
	if err != nil {
		panic(err)
	}

	//fmt.Println("nacosCli: ", gconv.String(client))
	return client
}
