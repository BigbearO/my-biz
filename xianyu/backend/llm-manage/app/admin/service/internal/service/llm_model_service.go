package service

import (
	"context"
	"fmt"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"google.golang.org/protobuf/types/known/emptypb"

	adminV1 "llm-manage/api/gen/go/admin/service/v1"
	"llm-manage/app/admin/service/internal/data"
	"llm-manage/app/admin/service/internal/data/models"
)

// LlmModelService 模型服务
type LlmModelService struct {
	adminV1.UnimplementedLlmModelServer
	log            *log.Helper
	repo           *data.LlmModelRepo
	providerRepo   *data.LlmProviderRepo
}

// NewLlmModelService 创建模型服务
func NewLlmModelService(ctx *bootstrap.Context, repo *data.LlmModelRepo, providerRepo *data.LlmProviderRepo) *LlmModelService {
	return &LlmModelService{
		log:          ctx.NewLoggerHelper("llm_model/service/admin-service"),
		repo:         repo,
		providerRepo: providerRepo,
	}
}

// List 模型列表
func (s *LlmModelService) List(ctx context.Context, req *adminV1.ListLlmModelRequest) (*adminV1.ListLlmModelReply, error) {
	page := req.GetPage()
	if page <= 0 {
		page = 1
	}
	pageSize := req.GetPageSize()
	if pageSize <= 0 {
		pageSize = 10
	}

	list, total, err := s.repo.List(ctx, page, pageSize, req.GetProviderId(), req.GetDisplayName(), req.GetStatus())
	if err != nil {
		return nil, err
	}

	reply := &adminV1.ListLlmModelReply{
		Total: total,
		List:  make([]*adminV1.LlmModelInfo, 0, len(list)),
	}

	for _, item := range list {
		info, err := s.toModelInfo(ctx, item)
		if err != nil {
			return nil, err
		}
		reply.List = append(reply.List, info)
	}

	return reply, nil
}

// Get 模型详情
func (s *LlmModelService) Get(ctx context.Context, req *adminV1.GetLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	model, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, fmt.Errorf("模型不存在")
	}

	return s.toModelInfo(ctx, model)
}

// Create 新增模型
func (s *LlmModelService) Create(ctx context.Context, req *adminV1.CreateLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	if req.GetProviderId() <= 0 {
		return nil, fmt.Errorf("请选择供应商")
	}
	if req.GetDisplayName() == "" {
		return nil, fmt.Errorf("模型显示名称不能为空")
	}
	if req.GetModelName() == "" {
		return nil, fmt.Errorf("实际模型名不能为空")
	}

	// 校验供应商存在
	provider, err := s.providerRepo.Get(ctx, req.GetProviderId())
	if err != nil {
		return nil, err
	}
	if provider == nil {
		return nil, fmt.Errorf("供应商不存在")
	}

	model := &models.LlmModel{
		ProviderID:  req.GetProviderId(),
		DisplayName: req.GetDisplayName(),
		ModelName:   req.GetModelName(),
		Temperature: float64(req.GetTemperature()),
		MaxTokens:   req.GetMaxTokens(),
		Status:      req.GetStatus(),
	}
	if model.Temperature == 0 {
		model.Temperature = 0.7
	}
	if model.MaxTokens == 0 {
		model.MaxTokens = 2048
	}

	if err := s.repo.Create(ctx, model); err != nil {
		return nil, err
	}

	return s.toModelInfo(ctx, model)
}

// Update 更新模型
func (s *LlmModelService) Update(ctx context.Context, req *adminV1.UpdateLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	model, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, fmt.Errorf("模型不存在")
	}

	// 校验供应商存在
	if req.GetProviderId() > 0 {
		provider, err := s.providerRepo.Get(ctx, req.GetProviderId())
		if err != nil {
			return nil, err
		}
		if provider == nil {
			return nil, fmt.Errorf("供应商不存在")
		}
		model.ProviderID = req.GetProviderId()
	}

	model.DisplayName = req.GetDisplayName()
	model.ModelName = req.GetModelName()
	model.Temperature = float64(req.GetTemperature())
	model.MaxTokens = req.GetMaxTokens()
	model.Status = req.GetStatus()

	if err := s.repo.Update(ctx, model); err != nil {
		return nil, err
	}

	return s.toModelInfo(ctx, model)
}

// Delete 删除模型
func (s *LlmModelService) Delete(ctx context.Context, req *adminV1.DeleteLlmModelRequest) (*emptypb.Empty, error) {
	model, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, fmt.Errorf("模型不存在")
	}

	if model.Status == 1 {
		return nil, fmt.Errorf("启用中的模型无法删除，请先停用")
	}

	if err := s.repo.Delete(ctx, req.GetId()); err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

// Enable 启用模型
func (s *LlmModelService) Enable(ctx context.Context, req *adminV1.EnableLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	model, err := s.repo.Get(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, fmt.Errorf("模型不存在")
	}

	enabledModel, err := s.repo.Enable(ctx, req.GetId())
	if err != nil {
		s.log.Errorf("enable model failed: %v", err)
		return nil, fmt.Errorf("启用模型失败")
	}

	return s.toModelInfo(ctx, enabledModel)
}

// toModelInfo 转换为 proto 结构
func (s *LlmModelService) toModelInfo(ctx context.Context, model *models.LlmModel) (*adminV1.LlmModelInfo, error) {
	info := &adminV1.LlmModelInfo{
		Id:          model.ID,
		ProviderId:  model.ProviderID,
		DisplayName: model.DisplayName,
		ModelName:   model.ModelName,
		Temperature: float32(model.Temperature),
		MaxTokens:   model.MaxTokens,
		Status:      model.Status,
		CreatedAt:   model.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt:   model.UpdatedAt.Format("2006-01-02 15:04:05"),
	}

	// 关联查询供应商名称
	provider, err := s.providerRepo.Get(ctx, model.ProviderID)
	if err != nil {
		return nil, err
	}
	if provider != nil {
		info.ProviderName = provider.Name
	}

	return info, nil
}
