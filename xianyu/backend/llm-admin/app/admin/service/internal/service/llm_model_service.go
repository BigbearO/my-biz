package service

import (
	"context"
	"log/slog"

	paginationV1 "github.com/tx7do/go-crud/api/gen/go/pagination/v1"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/xianyu/llm-admin/pkg/bootstrap"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data"

	llmModelV1 "github.com/xianyu/llm-admin/api/gen/go/llm_model/service/v1")

type LlmModelService struct {
	llmModelV1.UnimplementedLlmModelServiceServer

	log *slog.Logger

	llmModelRepo *data.LlmModelRepo
}

func NewLlmModelService(
    bs *bootstrap.Bootstrap,
    llmModelRepo *data.LlmModelRepo,
) *LlmModelService {
	return &LlmModelService{
		log:  bs.Logger.With("module", "llm_model/service"),
		llmModelRepo:  llmModelRepo,
	}
}

func (s *LlmModelService) List(ctx context.Context, req *paginationV1.PagingRequest) (*llmModelV1.ListLlmModelResponse, error) {
	return s.llmModelRepo.List(ctx, req)
}

func (s *LlmModelService) Get(ctx context.Context, req *llmModelV1.GetLlmModelRequest) (*llmModelV1.LlmModel, error) {
	return s.llmModelRepo.Get(ctx, req)
}

func (s *LlmModelService) Create(ctx context.Context, req *llmModelV1.CreateLlmModelRequest) (*llmModelV1.LlmModel, error) {
	if req == nil || req.Data == nil {
		return nil, llmModelV1.ErrorBadRequest("invalid parameter")
	}

    return s.llmModelRepo.Create(ctx, req)
}

func (s *LlmModelService) Update(ctx context.Context, req *llmModelV1.UpdateLlmModelRequest) (*emptypb.Empty, error) {
	if req == nil || req.Data == nil {
		return nil, llmModelV1.ErrorBadRequest("invalid parameter")
	}
	if _, err := s.llmModelRepo.Update(ctx, req); err != nil {
		return nil, err
	}
	return &emptypb.Empty{}, nil
}

func (s *LlmModelService) Delete(ctx context.Context, req *llmModelV1.DeleteLlmModelRequest) (*emptypb.Empty, error) {
    if req == nil {
        return nil, llmModelV1.ErrorBadRequest("invalid parameter")
    }

	deleted, err := s.llmModelRepo.Delete(ctx, req)
	if err != nil {
		return nil, err
	}
	if !deleted {
		return nil, llmModelV1.ErrorNotFound("llm_model not found")
	}
	return &emptypb.Empty{}, nil
}
