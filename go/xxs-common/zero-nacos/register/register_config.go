package register

import "fmt"

type Config struct {
	Name        string
	ListenOn    string
	NacosConfig NacosConfig
}

type NacosConfig struct {
	Host                string
	Port                uint64
	Username            string
	Password            string
	NamespaceId         string
	TimeoutMs           uint64
	NotLoadCacheAtStart bool
	LogLevel            string
}

// Target: nacos://nacos:nacos@127.0.0.1:8848/user-rpc?timeout=5000ms&namespaceid=xhf
const url = "nacos://%s:%s@%s:%d/%s?timeout=5000ms&namespaceid=%s"

func (c *NacosConfig) GetRpcAddr(serveName string) string {

	return fmt.Sprintf(url, c.Username, c.Password, c.Host, c.Port, serveName, c.NamespaceId)
}
