package config

import (
	"github.com/zeromicro/go-zero/rest"
)

type Config struct {
	rest.RestConf
	Elasticsearch struct {
		Addresses   []string `json:"addresses"`
		Username    string   `json:"username"`
		Password    string   `json:"password"`
		IndexPrefix string   `json:"index_prefix"`
	}
	Static string `json:"Static"`
	//Cache  cache.CacheConf
}
