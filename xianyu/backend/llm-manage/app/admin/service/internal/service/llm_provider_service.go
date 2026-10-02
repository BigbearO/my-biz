package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"google.golang.org/protobuf/types/known/emptypb"

	adminV1 "llm-manage/api/gen/go/admin/service/v1"
	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/data/models"
	"llm-manage/app/admin/service/internal/manager"
)

// LlmProviderService 供应商服务（业务编排层）
type LlmProviderService struct {
	adminV1.UnimplementedLlmProviderServer
	log     *log.Helper
	manager *manager.LlmProviderManager
}

// NewLlmProviderService 创建供应商服务
func NewLlmProviderService(ctx *bootstrap.Context, mgr *manager.LlmProviderManager) *LlmProviderService {
	return &LlmProviderService{
		log:     ctx.NewLoggerHelper(constants.LoggerNameLlmProviderService),
		manager: mgr,
	}
}

// List 供应商列表
func (s *LlmProviderService) List(ctx context.Context, req *adminV1.ListLlmProviderRequest) (*adminV1.ListLlmProviderReply, error) {
	page := req.GetPage()
	if page <= 0 {
		page = constants.DefaultPage
	}
	pageSize := req.GetPageSize()
	if pageSize <= 0 {
		pageSize = constants.DefaultPageSize
	}

	list, total, err := s.manager.ListWithPage(ctx, page, pageSize, req.GetName(), req.GetStatus())
	if err != nil {
		return nil, err
	}

	reply := &adminV1.ListLlmProviderReply{
		Total: total,
		List:  make([]*adminV1.LlmProviderInfo, 0, len(list)),
	}

	for _, item := range list {
		reply.List = append(reply.List, s.toProviderInfo(item))
	}

	return reply, nil
}

// Get 供应商详情
func (s *LlmProviderService) Get(ctx context.Context, req *adminV1.GetLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	provider, err := s.manager.GetByID(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if provider == nil {
		return nil, constants.ErrProviderNotFound
	}

	return s.toProviderInfo(provider), nil
}

// Create 新增供应商
func (s *LlmProviderService) Create(ctx context.Context, req *adminV1.CreateLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	// 参数校验
	if req.GetName() == "" {
		return nil, constants.ErrProviderNameEmpty
	}
	if req.GetBaseUrl() == "" {
		return nil, constants.ErrBaseURLEmpty
	}
	if req.GetApiKey() == "" {
		return nil, constants.ErrAPIKeyEmpty
	}

	status := req.GetStatus()
	if status == constants.StatusDisabled {
		status = constants.StatusEnabled
	}

	// 调用 manager 原子操作：加密后存库
	provider, err := s.manager.CreateWithEncryptedKey(ctx, req.GetName(), req.GetBaseUrl(), req.GetApiKey(), status)
	if err != nil {
		return nil, err
	}

	return s.toProviderInfo(provider), nil
}

// Update 更新供应商
func (s *LlmProviderService) Update(ctx context.Context, req *adminV1.UpdateLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	// 调用 manager 原子操作：更新（API Key 为空则不更新）
	provider, err := s.manager.UpdateWithOptionalKey(
		ctx,
		req.GetId(),
		req.GetName(),
		req.GetBaseUrl(),
		req.GetApiKey(),
		req.GetStatus(),
	)
	if err != nil {
		return nil, err
	}

	return s.toProviderInfo(provider), nil
}

// Delete 删除供应商
func (s *LlmProviderService) Delete(ctx context.Context, req *adminV1.DeleteLlmProviderRequest) (*emptypb.Empty, error) {
	// 业务规则：有关联模型时禁止删除
	count, err := s.manager.CountModels(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if count > 0 {
		return nil, fmt.Errorf(constants.MsgProviderHasModelsCount, count)
	}

	if err := s.manager.DeleteByID(ctx, req.GetId()); err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

// TestConnection 测试连接
func (s *LlmProviderService) TestConnection(ctx context.Context, req *adminV1.TestConnectionRequest) (*adminV1.TestConnectionReply, error) {
	var baseURL, apiKey string

	if req.GetId() > 0 {
		// 用已保存的配置（manager 负责查询和解密）
		provider, decryptedKey, err := s.manager.GetWithDecryptedKey(ctx, req.GetId())
		if err != nil {
			if err == constants.ErrProviderNotFound {
				return &adminV1.TestConnectionReply{
					Success: false,
					Message: constants.ErrProviderNotFound.Error(),
				}, nil
			}
			return nil, err
		}
		baseURL = provider.BaseURL
		apiKey = decryptedKey
	} else {
		// 用传入的配置
		baseURL = req.GetBaseUrl()
		apiKey = req.GetApiKey()
	}

	if baseURL == "" || apiKey == "" {
		return &adminV1.TestConnectionReply{
			Success: false,
			Message: constants.MsgTestConnectionConfigEmpty,
		}, nil
	}

	// 调用 OpenAI 兼容接口 /v1/models
	start := time.Now()
	url := strings.TrimRight(baseURL, "/") + "/v1/models"

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return &adminV1.TestConnectionReply{
			Success: false,
			Message: fmt.Sprintf("%s: %v", constants.MsgTestConnectionCreateRequestFailed, err),
		}, nil
	}
	httpReq.Header.Set("Authorization", "Bearer "+apiKey)

	client := &http.Client{Timeout: constants.HTTPTimeoutSeconds * time.Second}
	resp, err := client.Do(httpReq)
	latency := time.Since(start).Milliseconds()

	if err != nil {
		return &adminV1.TestConnectionReply{
			Success:   false,
			Message:   fmt.Sprintf("连接失败: %v", err),
			LatencyMs: latency,
		}, nil
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusOK {
		return &adminV1.TestConnectionReply{
			Success:   false,
			Message:   fmt.Sprintf("连接失败，状态码: %d，响应: %s", resp.StatusCode, string(body)),
			LatencyMs: latency,
		}, nil
	}

	// 解析响应，检查是否有 data 字段
	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return &adminV1.TestConnectionReply{
			Success:   true,
			Message:   constants.MsgTestConnectionSuccessNonStandard,
			LatencyMs: latency,
		}, nil
	}

	if _, ok := result["data"]; ok {
		return &adminV1.TestConnectionReply{
			Success:   true,
			Message:   constants.MsgTestConnectionSuccess,
			LatencyMs: latency,
		}, nil
	}

	return &adminV1.TestConnectionReply{
		Success:   true,
		Message:   constants.MsgTestConnectionSuccessNonStandard,
		LatencyMs: latency,
	}, nil
}

// toProviderInfo 转换为 proto 结构（脱敏 API Key）
func (s *LlmProviderService) toProviderInfo(provider *models.LlmProvider) *adminV1.LlmProviderInfo {
	return &adminV1.LlmProviderInfo{
		Id:        provider.ID,
		Name:      provider.Name,
		BaseUrl:   provider.BaseURL,
		ApiKey:    s.manager.MaskAPIKey(provider.APIKey),
		Status:    provider.Status,
		CreatedAt: provider.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt: provider.UpdatedAt.Format("2006-01-02 15:04:05"),
	}
}
