// Package service 业务编排层:实现 proto 生成的 Server 接口,委托 data 层。
package service

import (
	"context"
	"fmt"
	"log/slog"

	v1 "github.com/wind-scaffold/wind-template/api/gen/go/helloworld/service/v1"
)

// GreeterService 实现 Greeter 接口(grpc 与 http 共用同一实现)。
type GreeterService struct {
	v1.UnimplementedGreeterServer

	logger *slog.Logger
}

// NewGreeterService 构造服务实现。
func NewGreeterService(logger *slog.Logger) *GreeterService {
	return &GreeterService{logger: logger}
}

// SayHello 演示:回显问候语。
func (s *GreeterService) SayHello(ctx context.Context, req *v1.SayHelloRequest) (*v1.SayHelloReply, error) {
	name := req.GetName()
	if name == "" {
		name = "world"
	}
	s.logger.InfoContext(ctx, "收到问候", "name", name)
	return &v1.SayHelloReply{Message: fmt.Sprintf("hello, %s!", name)}, nil
}
