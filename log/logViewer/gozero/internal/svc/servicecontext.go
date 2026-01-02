package svc

import (
	"github.com/elastic/go-elasticsearch/v8"
	"log"
	"logViewer/gozero/internal/config"
)

type ServiceContext struct {
	Config   *config.Config
	ESClient *elasticsearch.Client
}

func NewServiceContext(cfg *config.Config) *ServiceContext {
	// 初始化 ES 客户端
	addresses := cfg.Elasticsearch.Addresses
	username := cfg.Elasticsearch.Username
	password := cfg.Elasticsearch.Password

	if len(addresses) == 0 {
		panic("elasticsearch.addresses is nil")
	}

	esCfg := elasticsearch.Config{
		Addresses: addresses,
		Username:  username,
		Password:  password,
	}

	esClient, err := elasticsearch.NewClient(esCfg)
	if err != nil {
		log.Fatalf("create es client err: %v", err)
	}

	return &ServiceContext{
		Config:   cfg,
		ESClient: esClient,
	}
}
