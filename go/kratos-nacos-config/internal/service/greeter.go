package service

import (
	"context"

	v1 "kratosNacosConfig/api/helloworld/v1"
	"kratosNacosConfig/internal/biz"
)

type GreeterService struct {
	v1.UnimplementedGreeterServer

	uc *biz.GreeterBiz
	nb *biz.NacosBiz
}

func NewGreeterService(uc *biz.GreeterBiz, nb *biz.NacosBiz) *GreeterService {
	return &GreeterService{
		uc: uc,
		nb: nb,
	}
}

func (s *GreeterService) SayHello(ctx context.Context, in *v1.HelloRequest) (*v1.HelloReply, error) {
	g, err := s.uc.CreateGreeter(ctx, &biz.Greeter{Hello: in.Name})
	if err != nil {
		return nil, err
	}
	return &v1.HelloReply{Message: "Hello " + g.Hello}, nil
}
