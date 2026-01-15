package http_resty

import (
	"github.com/go-resty/resty/v2"
	"time"
)

func NewRestyHttpClient() *resty.Client {
	client := resty.New()
	client.SetTimeout(time.Second) // 可以设置超时时间
	return client
}
