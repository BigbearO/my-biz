package service

import (
	"context"
	"log/slog"

	paginationV1 "github.com/tx7do/go-crud/api/gen/go/pagination/v1"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/xianyu/llm-admin/pkg/bootstrap"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data"

	llmProviderV1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1")

type LlmProviderService struct {
	llmProviderV1.UnimplementedLlmProviderServiceServer

	log *slog.Logger

	llmProviderRepo *data.LlmProviderRepo
}

func NewLlmProviderService(
    bs *bootstrap.Bootstrap,
    llmProviderRepo *data.LlmProviderRepo,
) *LlmProviderService {
	return &LlmProviderService{
		log:  bs.Logger.With("module", "llm_provider/service"),
		llmProviderRepo:  llmProviderRepo,
	}
}

func (s *LlmProviderService) List(ctx context.Context, req *paginationV1.PagingRequest) (*llmProviderV1.ListLlmProviderResponse, error) {
	return s.llmProviderRepo.List(ctx, req)
}

func (s *LlmProviderService) Get(ctx context.Context, req *llmProviderV1.GetLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	return s.llmProviderRepo.Get(ctx, req)
}

func (s *LlmProviderService) Create(ctx context.Context, req *llmProviderV1.CreateLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	if req == nil || req.Data == nil {
		return nil, llmProviderV1.ErrorBadRequest("invalid parameter")
	}

    return s.llmProviderRepo.Create(ctx, req)
}

func (s *LlmProviderService) Update(ctx context.Context, req *llmProviderV1.UpdateLlmProviderRequest) (*emptypb.Empty, error) {
	if req == nil || req.Data == nil {
		return nil, llmProviderV1.ErrorBadRequest("invalid parameter")
	}
	if _, err := s.llmProviderRepo.Update(ctx, req); err != nil {
		return nil, err
	}
	return &emptypb.Empty{}, nil
}

func (s *LlmProviderService) Delete(ctx context.Context, req *llmProviderV1.DeleteLlmProviderRequest) (*emptypb.Empty, error) {
    if req == nil {
        return nil, llmProviderV1.ErrorBadRequest("invalid parameter")
    }

	deleted, err := s.llmProviderRepo.Delete(ctx, req)
	if err != nil {
		return nil, err
	}
	if !deleted {
		return nil, llmProviderV1.ErrorNotFound("llm_provider not found")
	}
	return &emptypb.Empty{}, nil
}
