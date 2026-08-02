package service

import (
	"context"
	"strings"

	userpb "bear-admin/api/gen/go/user/v1"

	"bear-admin/app/ai/service/internal/data"

	"bigbear.com/xxs-common/xxs-otel-log/logx"
	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type UserService struct {
	userpb.UnimplementedUserServiceServer
	repo *data.UserRepo
	log  *log.Helper
}

func NewUserService(ctx *bootstrap.Context, repo *data.UserRepo) *UserService {
	return &UserService{
		repo: repo,
		log:  logx.NewTracedHelper(ctx.GetLogger(), "ai/service"),
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

func (s *UserService) CreateUser(ctx context.Context, req *userpb.CreateUserRequest) (*userpb.CreateUserReply, error) {
	id, err := s.repo.CreateUser(ctx, req.GetName())
	if err != nil {
		s.log.WithContext(ctx).Errorf("CreateUser failed: %v", err)
		return nil, err
	}
	// 回滚演练：仅 seata 开启时生效。name 以 rollback- 开头时，Go 分支已提交
	// （XA PREPARE + 上报 phase-1 成功），此处返回错误使 Java 发起方整体回滚，
	// 验证两端 XA 分支 phase-2 回滚一致性。
	if s.repo.Enabled() && strings.HasPrefix(req.GetName(), "rollback-") {
		s.log.WithContext(ctx).Infof("rollback drill triggered, name=%s", req.GetName())
		return nil, status.Error(codes.Aborted, "rollback drill")
	}
	return &userpb.CreateUserReply{
		Id:   id,
		Name: req.GetName(),
		Age:  req.GetAge(),
	}, nil
}
