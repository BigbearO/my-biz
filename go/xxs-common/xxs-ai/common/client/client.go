package client

import (
	"github.com/sashabaranov/go-openai"
	"xxs-ai/common/config"
)

func NewClientWithCustom(server string, token string) *openai.Client {
	conf := config.NewServerConfig(server, token)
	return openai.NewClientWithConfig(*conf)
}
