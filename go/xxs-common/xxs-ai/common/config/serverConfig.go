package config

import (
	"github.com/sashabaranov/go-openai"
)

// 这里的config协议选用OpenAI，DefaultConfig里面默认1了
func newServerAndEmptyLimitConfig(server string, token string, emptyMessagesLimit uint, apiVersion string) *openai.ClientConfig {
	// 由于sdk token命名小写，那没办法只能用这个方法
	config := openai.DefaultConfig(token)
	config.BaseURL = server
	config.EmptyMessagesLimit = emptyMessagesLimit
	config.APIVersion = apiVersion
	return &config
}

func NewServerConfig(server string, token string) *openai.ClientConfig {
	var limit = uint(SdkConf.EmptyMessagesLimit)
	var apiVersion = SdkConf.ApiVersion

	return newServerAndEmptyLimitConfig(server, token, limit, apiVersion)

}
