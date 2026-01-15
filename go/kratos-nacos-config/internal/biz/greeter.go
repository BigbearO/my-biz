package biz

import (
	"context"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/go-resty/resty/v2"
	"kratosNacosConfig/internal/conf"
)

type Greeter struct {
	Hello string
}

type GreeterRepo interface {
	Save(context.Context, *Greeter) (*Greeter, error)
}

type GreeterBiz struct {
	repo              GreeterRepo
	log               *log.Helper
	httpCli           *resty.Client
	nacosBaseYamlConf *conf.Bootstrap
}

func NewGreeterBiz(repo GreeterRepo, logger log.Logger, httpCli *resty.Client, nacosBaseYamlConf *conf.Bootstrap) *GreeterBiz {
	return &GreeterBiz{
		repo:              repo,
		log:               log.NewHelper(logger),
		httpCli:           httpCli,
		nacosBaseYamlConf: nacosBaseYamlConf,
	}
}

func (uc *GreeterBiz) CreateGreeter(ctx context.Context, g *Greeter) (*Greeter, error) {
	uc.log.WithContext(ctx).Infof("CreateGreeter: %v", g.Hello)
	return uc.repo.Save(ctx, g)
}
