package service

import (
	"context"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"

	llmV1 "llm-manage/api/gen/go/llm/service/v1"
	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/manager"
)

// LlmService 模型服务间契约服务（业务编排层）
//
// 区别于 admin 侧的 LlmProviderService/LlmModelService（后台 CRUD）：
// 本服务面向东西向调用（对话服务经 gRPC 取启用模型配置 / 回传用量），
// 不暴露 REST，仅在 gRPC 端口注册。
type LlmService struct {
	llmV1.UnimplementedLlmServiceServer
	log          *log.Helper
	providerMgr  *manager.LlmProviderManager
	cacheManager *manager.EnabledModelCacheManager
}

// NewLlmService 创建模型服务间契约服务
func NewLlmService(
	ctx *bootstrap.Context,
	providerMgr *manager.LlmProviderManager,
	cacheManager *manager.EnabledModelCacheManager,
) *LlmService {
	return &LlmService{
		log:          ctx.NewLoggerHelper(constants.LoggerNameLlmService),
		providerMgr:  providerMgr,
		cacheManager: cacheManager,
	}
}

// GetEnabledModel 获取当前启用的模型完整配置（含解密后 API Key，仅内网）
//
// 无启用模型时返回 NOT_FOUND（对话服务映射 50002，客户端不回复）。
// 缓存由本服务（提供方）负责：先读 Redis，未命中回源数据库并回填（TTL 5min）。
func (s *LlmService) GetEnabledModel(ctx context.Context, req *llmV1.GetEnabledModelRequest) (*llmV1.EnabledModel, error) {
	// 1. 先读缓存
	if cache := s.cacheManager.Get(ctx); cache != nil {
		return s.toEnabledModel(cache), nil
	}

	// 2. 缓存未命中：回源数据库（启用模型 + 供应商 + 解密 API Key）
	model, provider, apiKey, err := s.providerMgr.GetEnabledWithDecryptedKey(ctx)
	if err != nil {
		return nil, err
	}

	// 3. 回填缓存
	cache := &manager.EnabledModelCache{
		Id:           model.ID,
		ProviderName: provider.Name,
		BaseUrl:      provider.BaseURL,
		ApiKey:       apiKey,
		ModelName:    model.ModelName,
		Temperature:  model.Temperature,
		MaxTokens:    model.MaxTokens,
	}
	s.cacheManager.Set(ctx, cache)

	return s.toEnabledModel(cache), nil
}

// ReportUsage Token 用量回传（可选，用于成本核算）
//
// 本期先落结构化日志；后续接入用量报表时再落库，接口契约不变。
func (s *LlmService) ReportUsage(ctx context.Context, req *llmV1.ReportUsageRequest) (*llmV1.ReportUsageResponse, error) {
	s.log.Infof("report usage: item_id=%s model_name=%s prompt_tokens=%d completion_tokens=%d",
		req.GetItemId(), req.GetModelName(), req.GetPromptTokens(), req.GetCompletionTokens())

	return &llmV1.ReportUsageResponse{Success: true}, nil
}

// toEnabledModel 缓存结构转契约消息
func (s *LlmService) toEnabledModel(cache *manager.EnabledModelCache) *llmV1.EnabledModel {
	return &llmV1.EnabledModel{
		Id:           cache.Id,
		ProviderName: cache.ProviderName,
		BaseUrl:      cache.BaseUrl,
		ApiKey:       cache.ApiKey,
		ModelName:    cache.ModelName,
		Temperature:  cache.Temperature,
		MaxTokens:    cache.MaxTokens,
	}
}
