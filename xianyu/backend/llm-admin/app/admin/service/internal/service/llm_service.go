// llm_service.go 内部 gRPC 服务实现(仅供其他后端服务消费,无 REST 路由)。
package service

import (
	"context"
	"github.com/go-kratos/kratos/v3/errors"
	"log/slog"

	llmV1 "github.com/xianyu/llm-admin/api/gen/go/llm/service/v1"
	"github.com/xianyu/llm-admin/pkg/bootstrap"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data"
)

// LlmService 对外提供「当前启用模型」查询与用量回传。
type LlmService struct {
	llmV1.UnimplementedLlmServiceServer

	log          *slog.Logger
	llmModelRepo *data.LlmModelRepo
}

func NewLlmService(
	bs *bootstrap.Bootstrap,
	llmModelRepo *data.LlmModelRepo,
) *LlmService {
	return &LlmService{
		log:          bs.Logger.With("module", "llm/service"),
		llmModelRepo: llmModelRepo,
	}
}

// GetEnabledModel 返回当前启用模型的完整连接配置(api_key 为明文,仅限内网消费)。
func (s *LlmService) GetEnabledModel(ctx context.Context, req *llmV1.GetEnabledModelRequest) (*llmV1.EnabledModel, error) {
	dto, provider, err := s.llmModelRepo.GetEnabledWithProvider(ctx)
	if err != nil {
		return nil, err
	}
	if dto == nil || provider == nil {
		return nil, errors.NotFound("LLM", "no enabled model")
	}
	return &llmV1.EnabledModel{
		Id:           dto.GetId(),
		ProviderName: dto.GetProviderName(),
		BaseUrl:      provider.BaseUrl,
		ApiKey:       provider.ApiKey,
		ModelName:    dto.GetModelName(),
		Temperature:  float64(dto.GetTemperature()),
		MaxTokens:    dto.GetMaxTokens(),
	}, nil
}

// ReportUsage Token 用量回传(成本核算;第一期仅记录日志)。
func (s *LlmService) ReportUsage(ctx context.Context, req *llmV1.ReportUsageRequest) (*llmV1.ReportUsageResponse, error) {
	s.log.Info("llm usage",
		"item_id", req.GetItemId(),
		"model", req.GetModelName(),
		"prompt_tokens", req.GetPromptTokens(),
		"completion_tokens", req.GetCompletionTokens(),
	)
	return &llmV1.ReportUsageResponse{Success: true}, nil
}
