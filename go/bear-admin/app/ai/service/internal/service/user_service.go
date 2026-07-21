package service

import (
	"context"

	userpb "bear-admin/api/gen/go/user/v1"
)

type UserService struct {
	userpb.UnimplementedUserServiceServer
}

func NewUserService() *UserService {
	return &UserService{}
}

func (s *UserService) GetUser(ctx context.Context, req *userpb.GetUserRequest) (*userpb.GetUserReply, error) {
	return &userpb.GetUserReply{
		Id:   req.GetId(),
		Name: "test user",
		Age:  18,
	}, nil
}
