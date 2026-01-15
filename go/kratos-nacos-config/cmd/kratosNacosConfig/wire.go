//go:build wireinject
// +build wireinject

// The build tag makes sure the stub is not built in the final build.

package main

import (
	"kratosNacosConfig/internal/biz"
	"kratosNacosConfig/internal/client"
	"kratosNacosConfig/internal/conf"
	"kratosNacosConfig/internal/data"
	"kratosNacosConfig/internal/http_resty"
	"kratosNacosConfig/internal/server"
	"kratosNacosConfig/internal/service"

	"github.com/go-kratos/kratos/v2"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/google/wire"
)

// wireApp init kratos application.
func wireApp(*conf.Server, *conf.Data, log.Logger, *conf.Bootstrap) (*kratos.App, func(), error) {
	panic(wire.Build(server.ProviderSet, data.ProviderSet, biz.ProviderSet, service.ProviderSet, client.ProviderSet, http_resty.ProviderSet, newApp))
}
