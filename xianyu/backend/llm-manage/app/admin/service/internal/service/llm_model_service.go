package service

import (
	"context"

	"github.com/go-kratos/kratos/v2/log"
	"github.com/tx7do/kratos-bootstrap/bootstrap"
	"google.golang.org/protobuf/types/known/emptypb"

	adminV1 "llm-manage/api/gen/go/admin/service/v1"
	"llm-manage/app/admin/service/internal/constants"
	"llm-manage/app/admin/service/internal/data/models"
	"llm-manage/app/admin/service/internal/manager"
)

// LlmModelService 模型服务（业务编排层）
type LlmModelService struct {
	adminV1.UnimplementedLlmModelServer
	log     *log.Helper
	manager *manager.LlmModelManager
}

// NewLlmModelService 创建模型服务
func NewLlmModelService(ctx *bootstrap.Context, mgr *manager.LlmModelManager) *LlmModelService {
	return &LlmModelService{
		log:     ctx.NewLoggerHelper(constants.LoggerNameLlmModelService),
		manager: mgr,
	}
}

// List 模型列表
func (s *LlmModelService) List(ctx context.Context, req *adminV1.ListLlmModelRequest) (*adminV1.ListLlmModelReply, error) {
	page := req.GetPage()
	if page <= 0 {
		page = constants.DefaultPage
	}
	pageSize := req.GetPageSize()
	if pageSize <= 0 {
		pageSize = constants.DefaultPageSize
	}

	// 调用 manager 原子操作：分页查询并关联供应商名称
	list, providerNameMap, total, err := s.manager.ListWithProvider(
		ctx, page, pageSize, req.GetProviderId(), req.GetDisplayName(), req.GetStatus(),
	)
	if err != nil {
		return nil, err
	}

	reply := &adminV1.ListLlmModelReply{
		Total: total,
		List:  make([]*adminV1.LlmModelInfo, 0, len(list)),
	}

	for _, item := range list {
		reply.List = append(reply.List, s.toModelInfo(item, providerNameMap[item.ProviderID]))
	}

	return reply, nil
}

// Get 模型详情
func (s *LlmModelService) Get(ctx context.Context, req *adminV1.GetLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	// 调用 manager 原子操作：查询并关联供应商名称
	model, providerName, err := s.manager.GetWithProvider(ctx, req.GetId())
	if err != nil {
		return nil, err
	}

	return s.toModelInfo(model, providerName), nil
}

// Create 新增模型
func (s *LlmModelService) Create(ctx context.Context, req *adminV1.CreateLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	// 参数校验
	if req.GetProviderId() <= 0 {
		return nil, constants.ErrProviderIDRequired
	}
	if req.GetDisplayName() == "" {
		return nil, constants.ErrModelDisplayNameEmpty
	}
	if req.GetModelName() == "" {
		return nil, constants.ErrModelNameEmpty
	}

	// 调用 manager 原子操作：校验供应商存在后存库
	model, err := s.manager.CreateWithProviderCheck(
		ctx,
		req.GetProviderId(),
		req.GetDisplayName(),
		req.GetModelName(),
		float64(req.GetTemperature()),
		req.GetMaxTokens(),
		req.GetStatus(),
	)
	if err != nil {
		return nil, err
	}

	// 查询供应商名称用于返回
	_, providerName, err := s.manager.GetWithProvider(ctx, model.ID)
	if err != nil {
		return nil, err
	}

	return s.toModelInfo(model, providerName), nil
}

// Update 更新模型
func (s *LlmModelService) Update(ctx context.Context, req *adminV1.UpdateLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	// 调用 manager 原子操作：校验供应商存在后更新
	model, err := s.manager.UpdateWithProviderCheck(
		ctx,
		req.GetId(),
		req.GetProviderId(),
		req.GetDisplayName(),
		req.GetModelName(),
		float64(req.GetTemperature()),
		req.GetMaxTokens(),
		req.GetStatus(),
	)
	if err != nil {
		return nil, err
	}

	// 查询供应商名称用于返回
	_, providerName, err := s.manager.GetWithProvider(ctx, model.ID)
	if err != nil {
		return nil, err
	}

	return s.toModelInfo(model, providerName), nil
}

// Delete 删除模型
func (s *LlmModelService) Delete(ctx context.Context, req *adminV1.DeleteLlmModelRequest) (*emptypb.Empty, error) {
	// 业务规则：启用中的模型禁止删除
	model, err := s.manager.GetByID(ctx, req.GetId())
	if err != nil {
		return nil, err
	}
	if model == nil {
		return nil, constants.ErrModelNotFound
	}

	if model.Status == constants.StatusEnabled {
		return nil, constants.ErrModelEnabled
	}

	if err := s.manager.DeleteByID(ctx, req.GetId()); err != nil {
		return nil, err
	}

	return &emptypb.Empty{}, nil
}

// Enable 启用模型
func (s *LlmModelService) Enable(ctx context.Context, req *adminV1.EnableLlmModelRequest) (*adminV1.LlmModelInfo, error) {
	// 调用 manager 原子操作：事务内启用
	enabledModel, err := s.manager.EnableWithTransaction(ctx, req.GetId())
	if err != nil {
		return nil, err
	}

	// 查询供应商名称用于返回
	_, providerName, err := s.manager.GetWithProvider(ctx, enabledModel.ID)
	if err != nil {
		return nil, err
	}

	return s.toModelInfo(enabledModel, providerName), nil
}

// toModelInfo 转换为 proto 结构
func (s *LlmModelService) toModelInfo(model *models.LlmModel, providerName string) *adminV1.LlmModelInfo {
	return &adminV1.LlmModelInfo{
		Id:          model.ID,
		ProviderId:  model.ProviderID,
		ProviderName: providerName,
		DisplayName: model.DisplayName,
		ModelName:   model.ModelName,
		Temperature: float32(model.Temperature),
		MaxTokens:   model.MaxTokens,
		Status:      model.Status,
		CreatedAt:   model.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt:   model.UpdatedAt.Format("2006-01-02 15:04:05"),
	}
}
