package service

import (
	"context"

	userpb "bear-admin/api/gen/go/user/v1"

	"bigbear.com/xxs-common/xxs-otel-log/logx"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
)

type UserService struct {
	userpb.UnimplementedUserServiceServer
	log *log.Helper
}

func NewUserService(ctx *bootstrap.Context) *UserService {
	return &UserService{
		log: logx.NewTracedHelper(ctx.GetLogger(), "ai/service"),
	}
}

func (s *UserService) GetUser(ctx context.Context, req *userpb.GetUserRequest) (*userpb.GetUserReply, error) {
	s.log.WithContext(ctx).Info("GetUser called")
	return &userpb.GetUserReply{
		Id:   req.GetId(),
		Name: "test user",
		Age:  18,
	}, nil
}
