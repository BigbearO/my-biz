package es

import (
	"fmt"
	"os"
	"strings"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
	"github.com/spf13/viper"
)

// 日志文档结构（可根据实际字段扩展）
type LogDoc struct {
	Timestamp  string `json:"@timestamp"`
	Level      string `json:"level"` // debug/info/warn/error
	Message    string `json:"message"`
	App        string `json:"app"`
	TraceID    string `json:"trace_id,omitempty"`
	LoggerName string `json:"logger_name"`
	ThreadName string `json:"thread_name"`
	Env        string `json:"env"`
}

// 初始化 ES 客户端
func NewESClient() *elasticsearch.Client {
	addressList := os.Getenv("LOG_ES_ADDR")
	var addresses []string = nil
	if addressList != "" {
		addresses = strings.Split(addressList, ",")
	}
	if len(addresses) == 0 {
		addresses = viper.GetStringSlice("elasticsearch.addresses")
	}

	username := viper.GetString("elasticsearch.username")
	password := viper.GetString("elasticsearch.password")

	if addresses == nil {
		panic("elasticsearch.addresses is nil")
	}

	cfg := elasticsearch.Config{
		Addresses: addresses,
		Username:  username,
		Password:  password,
	}
	es, err := elasticsearch.NewClient(cfg)
	if err != nil {
		panic(fmt.Sprintf("create es client err: %v", err))
	}
	return es
}

// 统一响应格式
type Resp struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data,omitempty"`
}
