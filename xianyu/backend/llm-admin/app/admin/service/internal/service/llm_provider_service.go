package service

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	paginationV1 "github.com/tx7do/go-crud/api/gen/go/pagination/v1"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/xianyu/llm-admin/pkg/bootstrap"

	"github.com/xianyu/llm-admin/app/admin/service/internal/data"

	llmProviderV1 "github.com/xianyu/llm-admin/api/gen/go/llm_provider/service/v1"
)

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
		log:             bs.Logger.With("module", "llm_provider/service"),
		llmProviderRepo: llmProviderRepo,
	}
}

// maskAPIKey 出参脱敏:保留前 3 位与后 4 位,中间打码;短/空 key 直接打码。
func maskAPIKey(k string) string {
	if k == "" {
		return ""
	}
	if len(k) <= 8 {
		return "******"
	}
	return k[:3] + "******" + k[len(k)-4:]
}

func (s *LlmProviderService) List(ctx context.Context, req *paginationV1.PagingRequest) (*llmProviderV1.ListLlmProviderResponse, error) {
	resp, err := s.llmProviderRepo.List(ctx, req)
	if err != nil {
		return nil, err
	}
	for _, item := range resp.GetItems() {
		item.ApiKey = ptr(maskAPIKey(item.GetApiKey()))
	}
	return resp, nil
}

func ptr[T any](v T) *T { return &v }

func (s *LlmProviderService) Get(ctx context.Context, req *llmProviderV1.GetLlmProviderRequest) (*llmProviderV1.LlmProvider, error) {
	dto, err := s.llmProviderRepo.Get(ctx, req)
	if err != nil {
		return nil, err
	}
	if dto != nil {
		dto.ApiKey = ptr(maskAPIKey(dto.GetApiKey()))
	}
	return dto, nil
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

// TestConnection 测试供应商连通性:GET {base_url}/v1/models(Bearer api_key)。
// 传 id 用已保存配置;不传 id 用 base_url+api_key。
func (s *LlmProviderService) TestConnection(ctx context.Context, req *llmProviderV1.TestConnectionRequest) (*llmProviderV1.TestConnectionReply, error) {
	var baseURL, apiKey string

	if req.GetId() > 0 {
		dto, err := s.llmProviderRepo.Get(ctx, &llmProviderV1.GetLlmProviderRequest{
			QueryBy: &llmProviderV1.GetLlmProviderRequest_Id{Id: req.GetId()},
		})
		if err != nil {
			return nil, err
		}
		if dto == nil {
			return &llmProviderV1.TestConnectionReply{Success: false, Message: "provider not found"}, nil
		}
		baseURL, apiKey = dto.GetBaseUrl(), dto.GetApiKey()
	} else {
		baseURL, apiKey = req.GetBaseUrl(), req.GetApiKey()
	}

	if baseURL == "" || apiKey == "" {
		return &llmProviderV1.TestConnectionReply{Success: false, Message: "base_url 或 api_key 为空"}, nil
	}

	start := time.Now()
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodGet, strings.TrimRight(baseURL, "/")+"/v1/models", nil)
	if err != nil {
		return &llmProviderV1.TestConnectionReply{Success: false, Message: err.Error()}, nil
	}
	httpReq.Header.Set("Authorization", "Bearer "+apiKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(httpReq)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return &llmProviderV1.TestConnectionReply{
			Success:   false,
			Message:   fmt.Sprintf("连接失败: %v", err),
			LatencyMs: latency,
		}, nil
	}
	defer resp.Body.Close()
	_, _ = io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusOK {
		return &llmProviderV1.TestConnectionReply{
			Success:   false,
			Message:   fmt.Sprintf("HTTP %d", resp.StatusCode),
			LatencyMs: latency,
		}, nil
	}
	return &llmProviderV1.TestConnectionReply{Success: true, Message: "OK", LatencyMs: latency}, nil
}
