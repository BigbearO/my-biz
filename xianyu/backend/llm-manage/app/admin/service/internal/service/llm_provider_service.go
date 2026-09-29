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
	"llm-manage/app/admin/service/internal/data"
	"llm-manage/app/admin/service/internal/data/models"
)

// LlmProviderService 供应商服务
type LlmProviderService struct {
	adminV1.UnimplementedLlmProviderServer
	log       *log.Helper
	repo      *data.LlmProviderRepo
	encryptor *Encryptor
}

// NewLlmProviderService 创建供应商服务
func NewLlmProviderService(ctx *bootstrap.Context, repo *data.LlmProviderRepo, encryptor *Encryptor) *LlmProviderService {
	return &LlmProviderService{
		log:       ctx.NewLoggerHelper("llm_provider/service/admin-service"),
		repo:      repo,
		encryptor: encryptor,
	}
}

// List 供应商列表
func (s *LlmProviderService) List(ctx context.Context, req *adminV1.ListLlmProviderRequest) (*adminV1.ListLlmProviderReply, error) {
	page := req.GetPage()
	if page <= 0 {
		page = 1
	}
	pageSize := req.GetPageSize()
	if pageSize <= 0 {
		pageSize = 10
	}

	list, total, err := s.repo.List(ctx, page, pageSize, req.GetName(), req.GetStatus())
	if err != nil {
		return nil, err
	}

	reply := &adminV1.ListLlmProviderReply{
		Total: total,
		List:  make([]*adminV1.LlmProviderInfo, 0, len(list)),
	}

	for _, item := range list {
		reply.List = append(reply.List, s.toProviderInfo(item, true))
	}

	return reply, nil
}

// Get 供应商详情
func (s *LlmProviderService) Get(ctx context.Context, req *adminV1.GetLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	provider, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if provider == nil {
		return nil, fmt.Errorf("供应商不存在")
	}

	return s.toProviderInfo(provider, true), nil
}

// Create 新增供应商
func (s *LlmProviderService) Create(ctx context.Context, req *adminV1.CreateLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	if req.GetName() == "" {
		return nil, fmt.Errorf("供应商名称不能为空")
	}
	if req.GetBaseUrl() == "" {
		return nil, fmt.Errorf("接口地址不能为空")
	}
	if req.GetApiKey() == "" {
		return nil, fmt.Errorf("API Key 不能为空")
	}

	// 加密 API Key
	encryptedKey, err := s.encryptor.Encrypt(req.GetApiKey())
	if err != nil {
		s.log.Errorf("encrypt api key failed: %v", err)
		return nil, fmt.Errorf("加密 API Key 失败")
	}

	provider := &models.LlmProvider{
		Name:    req.GetName(),
		BaseURL: req.GetBaseUrl(),
		APIKey:  encryptedKey,
		Status:  req.GetStatus(),
	}
	if provider.Status == 0 {
		provider.Status = 1
	}

	if err := s.repo.Create(ctx, provider); err != nil {
		return nil, err
	}

	return s.toProviderInfo(provider, true), nil
}

// Update 更新供应商
func (s *LlmProviderService) Update(ctx context.Context, req *adminV1.UpdateLlmProviderRequest) (*adminV1.LlmProviderInfo, error) {
	provider, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if provider == nil {
		return nil, fmt.Errorf("供应商不存在")
	}

	provider.Name = req.GetName()
	provider.BaseURL = req.GetBaseUrl()
	provider.Status = req.GetStatus()

	// API Key 不为空才更新
	if req.GetApiKey() != "" {
		encryptedKey, err := s.encryptor.Encrypt(req.GetApiKey())
		if err != nil {
			s.log.Errorf("encrypt api key failed: %v", err)
			return nil, fmt.Errorf("加密 API Key 失败")
		}
		provider.APIKey = encryptedKey
	}

	if err := s.repo.Update(ctx, provider); err != nil {
		return nil, err
	}

	return s.toProviderInfo(provider, true), nil
}

// Delete 删除供应商
func (s *LlmProviderService) Delete(ctx context.Context, req *adminV1.DeleteLlmProviderRequest) (*emptypb.Empty, error) {
	// 检查是否有关联模型
	count, err := s.repo.CountModelsByProviderID(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if count > 0 {
		return nil, fmt.Errorf("该供应商下存在 %d 个模型，无法删除", count)
	}

	if err := s.repo.Delete(ctx, req.GetId()); err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

// TestConnection 测试连接
func (s *LlmProviderService) TestConnection(ctx context.Context, req *adminV1.TestConnectionRequest) (*adminV1.TestConnectionReply, error) {
	var baseURL, apiKey string

	if req.GetId() > 0 {
		// 用已保存的配置
		provider, err := s.repo.Get(ctx, req.GetId())
		if err != nil {
			return nil, err
		}
		if provider == nil {
			return &adminV1.TestConnectionReply{
				Success: false,
				Message: "供应商不存在",
			}, nil
		}
		baseURL = provider.BaseURL
		// 解密 API Key
		decryptedKey, err := s.encryptor.Decrypt(provider.APIKey)
		if err != nil {
			s.log.Errorf("decrypt api key failed: %v", err)
			return &adminV1.TestConnectionReply{
				Success: false,
				Message: "解密 API Key 失败",
			}, nil
		}
		apiKey = decryptedKey
	} else {
		// 用传入的配置
		baseURL = req.GetBaseUrl()
		apiKey = req.GetApiKey()
	}

	if baseURL == "" || apiKey == "" {
		return &adminV1.TestConnectionReply{
			Success: false,
			Message: "接口地址和 API Key 不能为空",
		}, nil
	}

	// 调用 OpenAI 兼容接口 /v1/models
	start := time.Now()
	url := strings.TrimRight(baseURL, "/") + "/v1/models"

	httpReq, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return &adminV1.TestConnectionReply{
			Success: false,
			Message: fmt.Sprintf("创建请求失败: %v", err),
		}, nil
	}
	httpReq.Header.Set("Authorization", "Bearer "+apiKey)

	client := &http.Client{Timeout: 10 * time.Second}
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
			Message:   "连接成功（响应格式非标准）",
			LatencyMs: latency,
		}, nil
	}

	if _, ok := result["data"]; ok {
		return &adminV1.TestConnectionReply{
			Success:   true,
			Message:   "连接成功",
			LatencyMs: latency,
		}, nil
	}

	return &adminV1.TestConnectionReply{
		Success:   true,
		Message:   "连接成功（响应格式非标准）",
		LatencyMs: latency,
	}, nil
}

// toProviderInfo 转换为 proto 结构
func (s *LlmProviderService) toProviderInfo(provider *models.LlmProvider, mask bool) *adminV1.LlmProviderInfo {
	apiKey := provider.APIKey
	if mask {
		// 先解密再脱敏
		decryptedKey, err := s.encryptor.Decrypt(provider.APIKey)
		if err == nil {
			apiKey = MaskAPIKey(decryptedKey)
		} else {
			apiKey = "****"
		}
	}

	return &adminV1.LlmProviderInfo{
		Id:        provider.ID,
		Name:      provider.Name,
		BaseUrl:   provider.BaseURL,
		ApiKey:    apiKey,
		Status:    provider.Status,
		CreatedAt: provider.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt: provider.UpdatedAt.Format("2006-01-02 15:04:05"),
	}
}
