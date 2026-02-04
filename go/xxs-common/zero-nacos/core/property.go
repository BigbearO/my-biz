package core

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
